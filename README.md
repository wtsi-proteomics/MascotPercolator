# MascotPercolator

Overview:
Sound scoring methods for idetifying mass spectra using sequence database search algorithms such as Mascot and Sequest are essential for sensitive and accurate peptide and protein identifications from proteomic tandem mass spectrometry data. Here we present a software package that interfaces Mascot with Percolator, a well performing machine learning method for rescoring database search results, and demonstrate it to be amenable for both low and high accuracy mass spectrometry data, outperforming all available Mascot scoring schemes as well as providing reliable significance measures. MascotPercolator can be readily used as a stand alone tool or integrated into existing data analysis pipelines.

Download and Installation:
* Install the  Java runtime environment, version 1.5 or higher - http://www.java.com/en/download
* Download the MascotPercolator package and unzip the folder
* Download Mascot Parser - http://www.matrixscience.com/msparser_download.html 
* Extract the Mascot Parser files and copy everything from within the java subfolder into the root of the MascotPercolator folder. That should comprise two files: msparser.jar, libmsparserj.so (for Linux only) and msparser.dll (for Windows only)
* Download Percolator (For MascotPercolator v2.01 or earlier you will need to use Percolator v1.14. MascotPercolator v2.02 onwards supports later versions of Percolator) - http://noble.gs.washington.edu/proj/percolator/
* Compile Percolator as described in its included documentation
* Update the config.properties file, available in the root folder of MascotPercolator. (Make sure that the path to Percolator executable is specified correctly)
* To test whether Mascot Percolator can be executed, enter "java -jar MascotPercolator.jar"

For help regarding the installation or execution, feel free to contact James Wright.

Usage:

To run MascotPercolator enter the following in a command prompt or unix shell:

java -cp MascotPercolator.jar cli.MascotPercolator [options...]

Parameters (replacing the "[options ...]" expression):

target VAL (required) - Log ID [1] or path/file name of the Mascot target results .dat file
decoy VAL (required) - Log ID [1] or path/file name of the Mascot decoy results dat file. Note: if Mascot's 'auto-decoy' mode was used, use same logID/file as for the target parameter.
out VAL (required) - Results path and file name prefix (without extension). Will be used as prefix for output files.
overwrite (optional) - Given result files already exist, this option forces overwrite
validate FILE (optional) - File with a list of correct peptides/proteins (sequences simply concatenated or alternatively one sequence per line without identifiers)
rankdelta N (optional) - Maximum allowed Mascot score difference of peptide hit at hand as compared to top hit match. (Default = -1: If set to 1 all peptide hit ranks that have a delta score of < 1 to the top hit match are processed. A setting of -1 strictly reports only the top hit match of a spectrum.)
newDat (optional flag) - Write a new Mascot dat file that replaces the Mascot scores with Percolator's posterior error probabilities. newMascotScore = -10log10(PosteriorErrorProbability). The Mascot Identity Threshold is then set to 13 (score equivalent to posterior error probabilities <= 0.05). This option does not replace the existing dat files.
rt (optional/flag) - Enables retention time; will only be switched on when available from input data; default off; largely untested.
xml (optional/flag) - Write supplemental XML output as defined here: http://noble.gs.washington.edu/proj/percolator/model/percolator_out.xsd
features (optional/flag) - Write out feature file with results
chargefeature (optional/flag) - Switch to using a single value feature to represent precursor charge state rather than the standard 4 feature format
highcharge (optional/flag) - calculates series specific features for higher (up to 5+) fragment charge states
nofilter (optional/flag) - switches off filter which ignores spectra with less than 15 fragment peaks
u (optional/flag) - This flag switches Percolator between PSM mode and unique peptide mode. Using this option with the latest versions of Percolator and hence MascotPercolator report all PSMs rather than peptides. If using earlier versions of Percolator (pre v2.0) this will do the opposite and force Percolator and MascotPercolator to report only unique peptides. (only available in Mascot Percolator v2.02 onwards)
Example:

java -cp MascotPercolator.jar cli.MascotPercolator -rankdelta 1 -newDat -u -target 11083 -decoy 11084 -out 11083-11084

MascotPercolator extracts all necessary data from the Mascot dat file(s), trains Percolator and writes the results to the specified summary file. MascotPercolator requires a separate target and decoy search, which can be achieved in two ways:

A Mascot search is performed with the Mascot auto-decoy option enabled. In this case, the "-target" and "-decoy" parameter refer to the same logID or results file.
Two independent searches against a target and decoy database are performed, using identical search parameter settings. The "-target" and "-decoy" parameters are set accordingly.
Notes

[1] Note: Given the Mascot results are in the default results folder as specified in the config file, then the 'log ID' is the integer part of the Mascot result file of interest. Example: given /mascot/results/ is the root folder of the Mascot results and /mascot/results/20090330/F001234.dat is the results file of interest, then the 'log ID' would be 1234.

Contact: 
James Wright (james.wright@sanger.ac.uk)
