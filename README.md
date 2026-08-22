# Scouting Collector

Hello, this is my attempt at making a Scouting Server that does the following: <br/>
- Keeps the original idea of the server + improves the experimental/new features (like swapping between different data formats)<br/>
- Allows toggling between Bluetooth and QRScanner collections<br/>
- Has an easier system to swap around data formats that don't require remaking the server every time<br/>
- Uses Java, since the scouting team is already trained in that and can therefore easily change the server <br/>
- Previews some of the data, to quickly see what scouts have sent without checking the CSV file<br/>
- Keeps track of how many people have sent connected, trying to also keep track of when data corrupts vs doesn't send<br/>
<br/>
I believe I succeeded in all of these, though if you're reading this it's likely something broke.<br/>
<br/>
Usage: <br/>
- Add/Change .json files to add data formats, there should be atleast one file provided as a sample (if there isn't, there's 2 examples in this repo)<br/>
- The app UI should be self-explanatory<br/>
- QRCodes are meant to scan CSV file data, and Bluetooth should connect to COM ports. They're designed to be interchangeable in receiving data as well as the app is interchangeable about sending data. <br/>
  <br/>
and yes, these errors are normal, don't know why they're here:<br/>
SLF4J: Failed to load class "org.slf4j.impl.StaticLoggerBinder".<br/>
SLF4J: Defaulting to no-operation (NOP) logger implementation<br/>
SLF4J: See http://www.slf4j.org/codes.html#StaticLoggerBinder for further details.<br/>
<br/>
Development / Updating:<br/>
<br/>
The app is very import-based, so it's difficult to learn. <br/>
<br/>
App specs:<br/>
- Made using IntelliJ Community edition<br/>
- Ran using Gradle (JavaFX is weird)<br/>
- Made in JDK 17, probably can be translated into a more modern version<br/>
<br/>
You export the app by putting "./gradlew jpackageImage" in command line, and (assuming everything is installed right) your folder will appear under ..\ScoutingCollector\build\jpackage<br/>
