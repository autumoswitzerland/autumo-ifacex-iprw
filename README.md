# README

## autumo ifaceX - Additional Interface Processor Readers & Writers

This is a maven eclipse project and serves consist of additional autumo ifaceX 
Interface Processor (IP) Readers and Writers and is extending the core reader
and writer library of the autumo ifaceX platform.

If you want to install the project properly, you need the Maven console
tools installed on your computer (e.g. command 'mvn') and a local Maven
repository.

Of course, you can also just look at the source files and use what you need
in your own project and IDE.

### Install: Plug into ifaceX

1.	Export the ZIP archive.

2.	Copy all libraries from the lib/ folder to the ifaceX-lib/ folder.

3.	Comment out the corresponding readers and writers in the ifaceX
	configuration 'cfg/interfaces.cfg'; see section 'Additional Writers'
	and 'Additional Readers'. This can be done in the ifaceX Studio too;
	see Menu 'Configuration' -> 'Interfaces Configuration'.

### Build

Java 21 or newer is required.

1.	You can Import the project in eclipse or in any other development IDE.

2.	Copy the following libraries from the ifaceX Installation directory'
	into the 'lib/'-directory of this project:
	
	- lib/autumo-commons-x.y.z.jar
	- lib/autumo-ifacex-x.y.z.jar
	- lib/autumo-beetroot-x.y.z.jar
	
3.	Execute 'maven-install.sh|bat' so above libraries are installed into 
	your local Maven repository; only available with autumo ifaceX, see
	https://products.autumo.ch.
	
4.	Execute 'make.sh' to create a package that can be deployed into an
	ifaceX installation directory. 

### Testing

#### Unit Tests (Mocks, offline)

All reader/writer modules have offline regression tests using mocks.

	bin/run-tests.sh            # all modules
	bin/run-tests.sh mod-ftp    # single module

or directly:

	mvn clean test

#### Integration Tests (real services, optional)

Integration tests run against real services and are skipped unless the
required environment variables are set.

1. Copy the template and fill in your values:

	cp bin/.env.example bin/.env

2. Run locally (loads bin/.env automatically):

	bin/run-integration-tests.sh            # all modules
	bin/run-integration-tests.sh mod-ftp    # single module

or directly:

	mvn verify              # runs *IntegrationTest classes via Failsafe

Available variables: FTP_TEST_*, SFTP_TEST_*, WEBDAV_TEST_*, AWS_TEST_*,
B2_TEST_*, DROPBOX_TEST_TOKEN, GDRIVE_TEST_*, GCS_TEST_*, AZURE_TEST_*,
OS_TEST_*, NIFI_TEST_* (see bin/.env.example for details).

#### Local emulators (no cloud account/credit card needed)

AWS S3 (Adobe S3Mock) and Azure Blob Storage (Azurite) can be tested against local
Docker emulators:

	mod-az-aws-s3/test/docker.sh start    # S3Mock, endpoint http://localhost:9090
	mod-ms-azure/test/docker.sh start     # Azurite, endpoint http://127.0.0.1:10000

Then set in bin/.env:

	AWS_TEST_EMULATOR=1
	AZURE_TEST_EMULATOR=1

and run:

	bin/run-integration-tests.sh mod-az-aws-s3 mod-ms-azure

Stop the emulators with `docker.sh stop`.

### Additional Readers | Writers

- Amazon AWS S3 : Read and write files via blops.
- DropBox : Read and write files.
- Google Cloud Storage : Read and write files via blops.
- Google Drive : Read and write files.
- WebDAV (with or without SSL) : Read and write files.
- FTP (with or without SSL) : Read and write files.
- SFTP : Read and write files.
- OpenStack (Swift ObjectStorage, Keystone V2 and V3) : Read and write files via blops.
- Microsoft Azure (Blob Storage) : Read and write files via blops.
- BackBlaze B2 Storage : Read and write files.
- Apache NiFi (Site-to-Site) : Read and write FlowFiles.

#### ifaceX Core Readers | Writers

- Null Reader : Reads nothing, is used when the IP itself produces data.
- CSV Reader | Writer : CSV files
- JSON Reader | Writer : JSON files
- File Reader | Writer : Does not read or write contents if a file, only for file transports.
- Document Reader : Extracts full text (over 80 formats, optional package).
- Database Reader | Writer : Read and write relational databases.
- MongoDB Reader | Writer : Read and write MongoDB databases.
- REST Reader | Writer : Read and write universal JSON REST APIs.
- First Writer : Only writes 1st batch.
- Console Writer : Writes to console.
- Mail Writer : Sends CSV files.
- Code Writer : Executes code.
- Exec : Executes OS programs.
 
Above Core Readers and Writers have many Interface Processor Configurations (IPCs)
templates that connect to HubSpot, PayPal, Spotify or financial APIs, etc.

If you want to run the additional Readers and Writers independently from autumo ifaceX,
you might need to make small code adjustments (e.g., get SSL context differently) and
you can find all necessary API interfaces and objects in the ifaceX Developer project:

https://github.com/autumoswitzerland/autumo-ifacex-developer

