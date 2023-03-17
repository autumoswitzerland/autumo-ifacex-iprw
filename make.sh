#!/bin/sh

###############################################################################
#
#  autumo ifaceX Additional Reader/Writer packager.
#  Version: 1.0
#
#  Notes:
#   -
#
#------------------------------------------------------------------------------
#
#  2023 autumo GmbH
#  Date: 01.03.2023
#
###############################################################################




# VARS
IPRW_VERSION=1.0.0




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

	# copy libs
	cp ../../mod-az-aws-s3/lib/*.jar lib/
	cp ../../mod-bb-b2/lib/*.jar lib/
	cp ../../mod-dbox/lib/*.jar lib/
	cp ../../mod-ftp/lib/*.jar lib/
	cp ../../mod-google-drive/lib/*.jar lib/
	cp ../../mod-google-storage/lib/*.jar lib/
	cp ../../mod-ms-azure/lib/*.jar lib/
	cp ../../mod-ms-graph/lib/*.jar lib/
	cp ../../mod-os/lib/*.jar lib/
	cp ../../mod-sftp/lib/*.jar lib/
	cp ../../mod-webdav/lib/*.jar lib/
	
	cp ../../LICENSE.md .

	

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



