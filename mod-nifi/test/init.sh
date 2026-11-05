#!/usr/bin/env bash
#
# NiFi test-flow bootstrap for the ifacex NiFi reader/writer.
#
# Usage:
#   ./init.sh reader   # creates OUTPUT ports  articles, customers, orders
#   ./init.sh writer   # creates INPUT ports   articles, customers, orders
#
# NiFi requires public port names to be unique across the whole flow, so a
# reader and a writer port can never share a name. The two directions are
# therefore mutually exclusive: every run first removes all managed components
# and then creates only the ports for the requested direction.
#
set -euo pipefail

MODE="${1:-reader}"
case "$MODE" in
    reader|writer) ;;
    *) echo "Usage: $0 [reader|writer]" >&2; exit 2 ;;
esac

exec python3 - "$MODE" <<'PY'
import json
import os
import ssl
import sys
import time
import urllib.error
import urllib.parse
import urllib.request

BASE = os.getenv("NIFI_API_URL", "https://localhost:8443/nifi-api").rstrip("/")
USER = os.getenv("NIFI_USERNAME", "admin")
PWD = os.getenv("NIFI_PASSWORD", "ChangeThisPassword123!")
CTX = ssl._create_unverified_context()
TOKEN = None

MODE = sys.argv[1]

ENTITIES = {
    "articles": [
        {"id": 1, "title": "Introduction to NiFi", "author": "John Smith"},
        {"id": 2, "title": "Data Integration", "author": "Jane Miller"},
        {"id": 3, "title": "Processing Pipelines", "author": "David Brown"},
    ],
    "customers": [
        {"id": 101, "firstName": "Alice", "lastName": "Miller", "email": "alice@example.com"},
        {"id": 102, "firstName": "Bob", "lastName": "Smith", "email": "bob@example.com"},
        {"id": 103, "firstName": "Charlie", "lastName": "Jones", "email": "charlie@example.com"},
    ],
    "orders": [
        {"id": 1001, "customerId": 101, "amount": 149.90, "status": "completed"},
        {"id": 1002, "customerId": 102, "amount": 89.50, "status": "pending"},
        {"id": 1003, "customerId": 103, "amount": 249.00, "status": "shipped"},
    ],
}

GENERATOR_NAME = "Generate "
LOG_NAME = "Log "
DROP_TIMEOUT = 120


def request(method, path, payload=None, form=None):
    headers = {"Accept": "*/*"}
    if TOKEN:
        headers["Authorization"] = "Bearer " + TOKEN
    if form is not None:
        data = urllib.parse.urlencode(form).encode("utf-8")
        headers["Content-Type"] = "application/x-www-form-urlencoded"
    elif payload is not None:
        data = json.dumps(payload).encode("utf-8")
        headers["Content-Type"] = "application/json"
    else:
        data = None

    req = urllib.request.Request(BASE + path, data=data, headers=headers, method=method)
    try:
        with urllib.request.urlopen(req, context=CTX, timeout=30) as resp:
            body = resp.read().decode("utf-8")
            return body.strip() if form is not None else (json.loads(body) if body.strip() else {})
    except urllib.error.HTTPError as exc:
        raise RuntimeError(
            "NiFi API error %s: %s" % (exc.code, exc.read().decode("utf-8", "replace"))
        )


def component_id(item):
    return item.get("component", {}).get("id") or item.get("id")


def get_root():
    return request("GET", "/flow/process-groups/root")["processGroupFlow"]["id"]


def get_flow(group_id):
    return request("GET", "/flow/process-groups/" + group_id)["processGroupFlow"]["flow"]


def stop_flow(group_id):
    request("PUT", "/flow/process-groups/" + group_id, payload={"id": group_id, "state": "STOPPED"})


def drop_queue(connection_id):
    result = request("POST", "/flowfile-queues/" + connection_id + "/drop-requests")
    drop_id = result.get("dropRequest", {}).get("id")
    if not drop_id:
        raise RuntimeError("NiFi did not return a drop request id for " + connection_id)
    deadline = time.monotonic() + DROP_TIMEOUT
    while time.monotonic() < deadline:
        drop = request(
            "GET", "/flowfile-queues/" + connection_id + "/drop-requests/" + drop_id
        ).get("dropRequest", {})
        if drop.get("failureReason"):
            raise RuntimeError("Queue cleanup failed: " + drop["failureReason"])
        if drop.get("finished"):
            print("  emptied queue %s (%s flowfiles)" % (connection_id, drop.get("droppedCount", 0)))
            return
        time.sleep(1)
    raise RuntimeError("Timeout while emptying queue " + connection_id)


def delete_component(kind, item_id):
    # kind is the REST resource name: processors, output-ports, input-ports, connections
    result = request("GET", "/" + kind + "/" + item_id)
    revision = result.get("revision", {}).get("version", 0)
    request(
        "DELETE",
        "/" + kind + "/" + item_id + "?version=" + str(revision)
        + "&disconnectedNodeAcknowledged=true",
    )


def cleanup(root_id):
    print()
    print("Cleaning up previous test flow...")
    managed = set(ENTITIES)
    stop_flow(root_id)

    flow = get_flow(root_id)

    managed_ids = set()
    for key in ("processors", "outputPorts", "inputPorts"):
        for item in flow.get(key, []):
            name = item["component"]["name"]
            if name in managed or name.startswith(GENERATOR_NAME) or name.startswith(LOG_NAME):
                managed_ids.add(component_id(item))

    connections = [
        item for item in flow.get("connections", [])
        if component_id(item) in managed_ids
        or item.get("component", {}).get("source", {}).get("id") in managed_ids
        or item.get("component", {}).get("destination", {}).get("id") in managed_ids
    ]

    for item in connections:
        cid = component_id(item)
        print("Deleting connection:", cid)
        try:
            drop_queue(cid)
        except RuntimeError as exc:
            print("  skip queue drop:", exc)
        delete_component("connections", cid)

    for item in flow.get("processors", []):
        pid = component_id(item)
        if pid in managed_ids:
            print("Deleting processor:", item["component"]["name"])
            delete_component("processors", pid)

    for item in flow.get("outputPorts", []):
        pid = component_id(item)
        if pid in managed_ids:
            print("Deleting output port:", item["component"]["name"])
            delete_component("output-ports", pid)

    for item in flow.get("inputPorts", []):
        pid = component_id(item)
        if pid in managed_ids:
            print("Deleting input port:", item["component"]["name"])
            delete_component("input-ports", pid)

    for item in flow.get("processGroups", []):
        gid = component_id(item)
        name = item["component"]["name"]
        if name.startswith("ifacex-") or name.startswith("zz-"):
            print("Deleting process group:", name)
            stop_flow(gid)
            delete_component("process-groups", gid)

    print("Cleanup completed.")


def processor_type():
    for item in request("GET", "/flow/processor-types").get("processorTypes", []):
        if item.get("type") == "org.apache.nifi.processors.standard.GenerateFlowFile":
            return item
    raise RuntimeError("GenerateFlowFile processor type not found.")


def create_processor(root_id, name, records, ptype, index):
    result = request("POST", "/process-groups/" + root_id + "/processors", payload={
        "revision": {"version": 0},
        "component": {
            "type": ptype["type"],
            "bundle": ptype["bundle"],
            "name": name,
            "position": {"x": 400, "y": 200 + index * 200},
            "config": {
                "properties": {
                    "Custom Text": json.dumps(records, ensure_ascii=False),
                    "Data Format": "Text",
                    "Character Set": "UTF-8",
                    "Mime Type": "application/json",
                    "Batch Size": "1",
                    "Unique FlowFiles": "false",
                }
            },
        },
    })
    print("Created processor:", name)
    return component_id(result)


def create_log_processor(root_id, name, index):
    ptype = None
    for item in request("GET", "/flow/processor-types").get("processorTypes", []):
        if item.get("type") == "org.apache.nifi.processors.standard.LogAttribute":
            ptype = item
            break
    if ptype is None:
        raise RuntimeError("LogAttribute processor type not found.")
    result = request("POST", "/process-groups/" + root_id + "/processors", payload={
        "revision": {"version": 0},
        "component": {
            "type": ptype["type"],
            "bundle": ptype["bundle"],
            "name": name,
            "position": {"x": 900, "y": 200 + index * 200},
            "config": {
                "autoTerminatedRelationships": ["success"],
                "properties": {"Log Level": "info", "Log Payload": "true"},
            },
        },
    })
    print("Created processor:", name)
    return component_id(result)


def create_port(root_id, kind, name, index):
    result = request("POST", "/process-groups/" + root_id + "/" + kind, payload={
        "revision": {"version": 0},
        "component": {
            "parentGroupId": root_id,
            "name": name,
            "position": {"x": 800 if kind == "output-ports" else 1200, "y": 200 + index * 200},
            "allowRemoteAccess": True,
        },
    })
    print("Created %s: %s" % ("output port" if kind == "output-ports" else "input port", name))
    return component_id(result)


def create_connection(root_id, source_id, dest_id, source_type, dest_type):
    component = {
        "source": {"id": source_id, "groupId": root_id, "type": source_type},
        "destination": {"id": dest_id, "groupId": root_id, "type": dest_type},
    }
    # Local (non-port) sources need an explicit relationship; input ports have none.
    if source_type != "INPUT_PORT":
        component["selectedRelationships"] = ["success"]
    result = request("POST", "/process-groups/" + root_id + "/connections", payload={
        "revision": {"version": 0},
        "component": component,
    })
    print("Created connection.")
    return component_id(result)


def set_run_status(kind, item_id, state):
    result = request("GET", "/" + kind + "/" + item_id)
    revision = result.get("revision", {}).get("version", 0)
    request("PUT", "/" + kind + "/" + item_id + "/run-status", payload={
        "revision": {"version": revision},
        "state": state,
    })


def main():
    print("Initializing NiFi test flow (mode: %s)." % MODE)
    global TOKEN
    TOKEN = request("POST", "/access/token", form={"username": USER, "password": PWD}).strip()
    if not TOKEN:
        raise RuntimeError("NiFi authentication failed.")
    print("Authenticated with NiFi.")

    root_id = get_root()
    cleanup(root_id)

    print()
    print("Creating %d example entities for the %s side..." % (len(ENTITIES), MODE))

    ptype = processor_type()

    for index, (entity, records) in enumerate(ENTITIES.items()):
        print()
        print("Entity:", entity)

        if MODE == "reader":
            processor_id = create_processor(root_id, GENERATOR_NAME + entity, records, ptype, index)
            port_id = create_port(root_id, "output-ports", entity, index)
            create_connection(root_id, processor_id, port_id, "PROCESSOR", "OUTPUT_PORT")
            set_run_status("output-ports", port_id, "RUNNING")
            set_run_status("processors", processor_id, "RUN_ONCE")
            print("Output port + generator ready.")
        else:
            port_id = create_port(root_id, "input-ports", entity, index)
            logger_id = create_log_processor(root_id, LOG_NAME + entity, index)
            create_connection(root_id, port_id, logger_id, "INPUT_PORT", "PROCESSOR")
            set_run_status("processors", logger_id, "RUNNING")
            set_run_status("input-ports", port_id, "RUNNING")
            print("Input port ready (consumed by %s%s)." % (LOG_NAME, entity))

    print()
    print("NiFi initialization completed. Direction: %s" % MODE)
    print("Ports:", ", ".join(ENTITIES))


try:
    main()
except Exception as exc:
    print()
    print("NiFi initialization failed:", exc)
    sys.exit(1)
PY
