# Zbynek Legal Utilitilies - zbynek-legal-format command line utility

Command line utility to manipulate legal files.


## Usage

```
zbynek-legal-format [options] subcommand [options] arguments
```


## join-exhibit

```
zbynek-legal-format -o output.gpx join-exhibit -p start-page exhibit-inputs.cvs
```

The command joins exhibit documents specified in exhibit-inputs.csv into single
file, adds page numbers and updates the csv file with exhibits codes and their
page numbers.


## Build

You need to install:
- java (at least version 11)
- maven

Debian or Ubuntu:
```
sudo apt -y install maven
```

MacOs:
```
brew install maven
```

RedHat or Suse:
```
sudo yum -y install maven
```

Build:
```
git clone https://github.com/kvr000/zbynek-legal-util.git
cd zbynek-legal-util/
mvn -f zbynek-legal-format/ package

./zbynek-legal-format/target/zbynek-legal-format -h
```


## License

The code is released under version 2.0 of the [Apache License][].

## Stay in Touch

Feel free to contact me at kvr000@gmail.com  and http://github.com/kvr000/ and http://github.com/kvr000/zbynek-legal-util/

[Apache License]: http://www.apache.org/licenses/LICENSE-2.0
