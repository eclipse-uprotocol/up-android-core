# How to Install & Run Doxygen for IV UDiscoveryService

1. Download Doxygen and Graphviz  
    a) The following links provide binaries for both Linux & Windows  
    Doxygen:  https://www.doxygen.nl/download.html  
    Graphviz: http://graphviz.org/  

    b) Alternative Installation for Linux using APT  
```
    sudo apt update
    sudo apt install doxygen
    sudo apt install doxygen-gui
```
*The remaining steps were written for the Doxygen GUI (DoxyWizard)*  
*The same steps can be performed by manually executing commands via the Doxygen CLI*  

2. Open the IV UDiscoveryService Doxygen config file  
   File -> Open
   `~\gminfo\vendor\gm\sdv\packages\services\UDiscoveryService\service\src\main\doxygen\Doxyfile`

3. Specify the directory from which Doxygen will run  
   Click the Select button in the top right corner
   `~\gminfo\vendor\gm\sdv\packages\services\UDiscoveryService\service\src\main\doxygen`

4. Run Doxygen  
   Click the Run tab, click the Run doxygen button

5. Click Show HTML output  
   Enjoy!

### NOTES

The steps listed here were extracted from the following Youtube video, which provides
a more detailed step-by-step tutorial.  
   **How to Install and Use Doxygen (Doxygen Easy Tutorial)**
   https://www.youtube.com/watch?v=mgVgZjaeNkw&t=241s

Please refer to the Doxygen manual for further instructions on how to document the code  
   https://doxygen.nl/manual/index.html