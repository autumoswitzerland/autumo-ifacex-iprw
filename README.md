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

### Install

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

### Additional Readers | Writers

- Amazon AWS S3
- DropBox
- Google Cloud Storage
- Google Drive
- WebDAV (with SSL)
- FTP (with SSL)
- SFTP
- OpenStack (Keystone V2 and V3)
- Microsoft Azure Blob Storage

#### ifaceX Core Readers | Writers

 - Null Reader
 - CSV Reader | Writer
 - File Reader | Writer
 - Document Reader that extracts full text (over 80 formats)
 - Database Reader | Writer (Relational databases)
 - MongoDB Reader | Writer
 - REST Reader | Writer (Universal JSON REST)
 - First Writer (Only writes 1st batch)
 - Console Writer
 - Mail Writers (Sends CSV files)
 - Code Writer (Special: Executes code)
 - Exec (Special: Executes OS programs)
 
Above Core Readers and Writers have many Interface Processor Configurations (IPCs)
templates that connect to HubSpot, PayPal, Spotify or financial APIs, etc.

If you want to run the additional Readers and Writers independently from autumo ifaceX,
you might need to make small code adjustments (e.g., get SSL context differently) and
you can find all necessary API interfaces and objects in the ifaceX Developer project:

https://github.com/autumoswitzerland/autumo-ifacex-developer

