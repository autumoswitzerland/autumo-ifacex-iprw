#!/bin/sh

###############################################################################
#
#  autumo ifaceX Additional Reader/Writer distribution.
#  Version: 1.0
#
#  Notes:
#   - 'lftp' must be installed (sudo port install lftp).
#
#------------------------------------------------------------------------------
#
#  2023 autumo GmbH
#  Date: 12.07.2023
#
###############################################################################




# VARS
IPRW_VERSION=1.0.0
IFACEX_VERSION=1.2.0
MVDEST="/Volumes/Fastdrive/Applications/autumo/00 All Packages/ifaceX/$IFACEX_VERSION"
PASS='G!E56q8T0L'



# Clear
./make_all.sh clear


# Make
./make_all.sh



# -----------------------------
# ---- The distributions
# -----------------------------

cd product

# Upload
if [ "$1" = "upload" ]
then
	lftp ftp://autumo.ch:$PASS@211.hosttech.eu -e "cd products/downloads/ifacex; \
	put autumo-ifaceX-iprw-$IPRW_VERSION.zip; \
	bye;"
fi



# -----------------------------
# ---- Backup packages
# -----------------------------

# Move
mv -f *.zip "$MVDEST"

cd ..


