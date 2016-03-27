Quick steps to build on Ubuntu 15.10:

$ sudo apt-get install gradle git lib32z1 lib32stdc++6

$ mkdir ~/scratch; cd ~/scratch

$ git clone https://github.com/hackingtype1/xDrip-Experimental.git

$ wget http://dl.google.com/android/android-sdk_r24.4.1-linux.tgz

$ tar zxvf android-sdk_r24.4.1-linux.tgz

$ export ANDROID_HOME=~/scratch/android-sdk-linux

$ cd $ANDROID_HOME

$ tools/android list sdk --all

$ tools/android update sdk --all -u -t 10,29 #  Android SDK Build-tools, revision 22.0.1, SDK Platform Android 6.0, API 23, revision 2

$ tools/android update sdk --all -u -t 161,162 # Google Repository, Google Play services

$ tools/android update sdk --all -u -t 155 # Android Support Library, revision 23.2.1

$ tools/android update sdk --all -u -t 154 # Local Maven repository for Support Libraries, revision 28

$ cd ~/scratch/xDrip-Experimental/

$ gradle assemble

Copy ~/scratch/xDrip-Experimental/app/build/outputs/apk/app-xdrip-debug.apk to your device and install.

$ gradle build # integration tests etc

$ gradle clean # clean all
