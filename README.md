# gifme_app_deploy

## Steps to deploy MNN model to your android app
1. clone the MNN stable branch from their github [MNN](https://github.com/alibaba/MNN)
2. Build MNN for android by following next steps
3. set backend option ON/ OFF in CMakeList.txt in the root MNN directory
4. Download Android [NDK](https://developer.android.com/ndk/downloads/) and [SDK](https://developer.android.com/studio?gclsrc=aw.ds&&gclid=Cj0KCQjwpdqDBhCSARIsAEUJ0hMLJbqEM-md8YcUkhqZwdvcYgEzmfo2mozJNrMXKyvrC_Id6atd8wwaAnnoEALw_wcB) from google developers and set it to environment variable
5. cd /path/to/MNN
6. ./schema/generate.sh
7. cd project/android
8. Build armv7 library: mkdir build_32 && cd build_32 && ../build_32.sh
9. Build armv8 library: mkdir build_64 && cd build_64 && ../build_64.sh
10. Open Android studio create a new native project
11. Copy and Paste the .so files from project/android/build_32 into a new folder(armeabi-v7a) in the lib directory inside app directory of android project
12. Copy and Paste the .so files from project/android/build_64 into a new folder(arm64-v8a) in the lib directory inside app directory of android project
13. Copy and Paste the include directory from MNN base dir to android app directory
14. Now all the libs and headers are set its time to link them in CMakeList.txt
15. Follow this repos CMakeList.txt to follow the linking step
16. Now you are all set to use MNN in your android app
17. All the code related to creation of model ,session, tensor and inference are mentioned in native-lib.cpp
18. To change the backend of inference to VULKAN, OPENCL and CPU just open MainActivity.java navigate to line 65 and change the session backend there (Note the .so file related to each backend must be in the lib/{device-arch} directory)
19. place the model in asset folder and edit the shapes in MainActivity.java and native-lib.cpp
20. Run and get background image removal model inference in realtime.
