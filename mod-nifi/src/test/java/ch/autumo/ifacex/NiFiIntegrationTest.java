package ch.autumo.ifacex;

import static org.junit.jupiter.api.Assumptions.assumeTrue;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("integration")
class NiFiIntegrationTest {

    @Test
    void nifiIntegrationTestHint() {
        System.out.println("NiFi integration tests are run via Docker in mod-nifi/test/");
        System.out.println("Use docker.sh (compose.yaml) to start the Nifi container.");
        System.out.println("Use init.sh reader|writer to create Nifi ports.");
        assumeTrue(false, "No Java integration test for NiFi yet - use Docker setup in mod-nifi/test/");
    }
}
