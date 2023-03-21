ANDROIDSDK=/usr/lib/android-sdk/build-tools/debian
PLATFORM=/usr/lib/android-sdk/platforms/android-23/android.jar
# MINSDK=19
MINSDK=24
# MINSDK=26
APP=src/de/devtal/doorble

CLASSES=$(patsubst %.java,%.class,$(wildcard $(APP)/*.java))

# Resources:
# https://www.hanshq.net/command-line-android.html

doorble.apk: doorble.aligned.apk keystore.jks
	apksigner sign --ks keystore.jks --ks-key-alias androidkey --ks-pass pass:android --key-pass pass:android --out $@ $<

keystore.jks:
	keytool -genkeypair -keystore $@ -alias androidkey -validity 10000 -keyalg RSA -keysize 2048 -storepass android -keypass android

doorble.aligned.apk: doorble.unsigned.apk
	zipalign -f -p 4 $< $@

doorble.unsigned.apk: dex/classes.dex AndroidManifest.xml
	aapt package -f -F $@ -I $(PLATFORM) -M AndroidManifest.xml -S res dex
# -v

dex/classes.dex: $(APP)/DoorBLE.class $(CLASSES)
	[ -e dex ] || mkdir dex
	$(ANDROIDSDK)/dx --dex --min-sdk-version=$(MINSDK) --output=$@ src
# --verbose

%.class: %.java
	javac -bootclasspath $(PLATFORM) -classpath ./src -source 1.7 -target 1.7 $<

$(APP)/DoorBLE.class: $(APP)/JWTHandler.class $(APP)/Door.class $(APP)/R.class \
	$(APP)/FragmentLog.class $(APP)/FragmentMain.class \
	$(APP)/FragmentEditlist.class

$(APP)/Door.class: $(APP)/CryptoHelper.class

$(APP)/CryptoHelper.class:

$(APP)/JWTHandler.class: $(APP)/Door.class

$(APP)/FragmentLog.class: $(APP)/R.class

$(APP)/FragmentMain.class: $(APP)/Door.class $(APP)/R.class

$(APP)/FragmentEditlist.class: $(APP)/Door.class $(APP)/R.class

$(APP)/FragmentEditor.class: $(APP)/Door.class $(APP)/R.class

$(APP)/SuggestionTextView.class:

$(APP)/R.java: AndroidManifest.xml res/*
	aapt package -f -m -J src -S res -M AndroidManifest.xml -I $(PLATFORM)

.PHONY: clean distclean squeaky-clean
clean:
	rm -vf	$(APP)/R.java \
		$(APP)/*.class \
		*.unsigned.apk \
		*.aligned.apk \
		dex/*.dex

distclean: clean
	[ ! -d dex ] || rmdir dex
	rm -vf *.apk

squeaky-clean: distclean
	@echo 'Warning! This will remove your signing keys!'
	@echo 'You have 5 seconds to press CTRL-C'
	@sleep 5
	rm -vf *.jks
