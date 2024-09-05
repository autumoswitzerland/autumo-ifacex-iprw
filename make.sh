#!/bin/sh

###############################################################################
#
#  autumo ifaceX Additional Reader/Writer packager.
#  Version: 2.0
#
#  Notes:
#   -
#
#------------------------------------------------------------------------------
#
#  2023 autumo GmbH
#  Date: 07.12.2023
#
###############################################################################




# VARS
IPRW_VERSION=2.0.0




# ------------------------------------------------
# -------- Usage
# ------------------------------------------------
if [ "$1" = "help" -o "$#" -lt 1 ]
then
	echo " "
	echo "make clear"
	echo "make create"
	echo " "
	echo " "
	exit 1
fi


mkdir -p product


# ------------------------------------------------
# -------- DELETE PRODUCT
# ------------------------------------------------
if [ "$1" = "clear" ]
then
	cd product

	# remove working directory
	if [ -d "autumo-ifaceX-iprw-$IPRW_VERSION" ]
	then
		rm -Rf autumo-ifaceX-iprw-$IPRW_VERSION
	fi
	
	# remove package
	if [ -f "autumo-ifaceX-iprw-$IPRW_VERSION.zip" ]
	then
    	rm autumo-ifaceX-iprw-*.zip
	fi
	
	cd ..
	
	exit 1
fi



# ------------------------------------------------
# -------- CREATE PRODUCT
# ------------------------------------------------
if [ "$1" = "create" -a "$#" -gt 0 ]
then



# -----------------------------
# ---- Cleanup & Prepare
# -----------------------------

	echo "-> Cleanup & prepare..."
	
	# delete old product package
	if [ -d "product/autumo-ifaceX-iprw-$IPRW_VERSION" ]
	then
		rm -Rf product/autumo-ifaceX-iprw-$IPRW_VERSION
	fi	

	# go to product
	cd product
	
	# make working directory
	mkdir autumo-ifaceX-iprw-$IPRW_VERSION



# -----------------------------
# ---- Go to ifaceX package
# -----------------------------

	# go to ifaceX package folder
	cd autumo-ifaceX-iprw-$IPRW_VERSION



# -----------------------------
# ---- Copying
# -----------------------------
		

	echo "-> Copying..."
	
	mkdir -p lib


	##  Sorting out libs beforehand, example:
	#LIST=(`ls ../../mod-google-storage/lib/*.jar`)
	#LIST=${LIST[@]/*failureaccess*}
	#LIST=${LIST[@]/*google-oauth-client*}
	#LIST=${LIST[@]/*j2objc-annotations*}
	#LIST=${LIST[@]/*jsr305*}
	#LIST=${LIST[@]/*listenablefuture*}
	#cp $LIST lib/


	# COPY LIBS
	
	
	# Module 'mod-az-aws-s3'
	cp ../../mod-az-aws-s3/lib/*.jar lib/
	
	
	# Module 'mod-bb-b2'
	cp ../../mod-bb-b2/lib/*.jar lib/

	
	# Module 'mod-dbox'
	cp ../../mod-dbox/lib/*.jar lib/

	
	# Module 'mod-ftp'
	cp ../../mod-ftp/lib/*.jar lib/

	
	# Module 'mod-google-drive'
	cp ../../mod-google-drive/lib/*.jar lib/
	
	
	# Module 'mod-google-storage'
	cp ../../mod-google-storage/lib/*.jar lib/
	
	
	# Module 'mod-ms-azure'
	cp ../../mod-ms-azure/lib/*.jar lib/
	
	
	# Module 'mod-ms-graph'
	cp ../../mod-ms-graph/lib/*.jar lib/

	
	# Module 'mod-os'
	cp ../../mod-os/lib/*.jar lib/

	
	# Module 'mod-sftp'
	cp ../../mod-sftp/lib/*.jar lib/

	
	# Module 'mod-webdav'
	cp ../../mod-webdav/lib/*.jar lib/



	# REMOVE ifaceX Doublettes
	rm lib/bcprov-jdk15on*
	rm lib/checker-qual*
	rm lib/commons-codec*
	rm lib/commons-io*
	rm lib/commons-logging*
	rm lib/error_prone_annotations*
	rm lib/failureaccess*
	rm lib/guava-*
	rm lib/gson*
	rm lib/httpcore*
	rm lib/httpclient*
	rm lib/j2objc-annotations*
	rm lib/jackson-annotations*
	rm lib/jackson-core-*
	rm lib/jackson-databind*
	rm lib/jackson-dataformat-xml*
	rm lib/jackson-datatype-jsr310*
	rm lib/jakarta.activation-api-*
	rm lib/jna-*
	rm lib/json-20*
	rm lib/jsr305*
	rm lib/slf4j-api*
	rm lib/stax2-api*
	rm lib/woodstox-core*
	rm lib/listenablefuture*



	# COPY Templates
	
	mkdir -p interfaces
	cp ../../interfaces/*.* interfaces/

	
	
	# COPY LICENSE
	cp ../../LICENSE.md .
	# COPY README
	cp ../../README.md .

	

# -----------------------------
# ---- Create Product
# -----------------------------

	echo "-> Create PRODUCT..."

	# LEAVE PACKAGE FOLDER
	cd ..

	# create archive
	zip -r "autumo-ifaceX-iprw-${IPRW_VERSION}.zip" autumo-ifaceX-iprw-${IPRW_VERSION} \
		-x "*/.DS_Store" \
		-x "*/__MACOSX"

	# delete build directory
	rm -Rf autumo-ifaceX-iprw-${IPRW_VERSION}
	

	
# -----------------------------
# ---- END
# -----------------------------
	
	# leave product folder
	cd ..

	
else
	echo "Nope! -> make create"
	echo " "
fi



