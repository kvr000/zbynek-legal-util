# Zbynek Legal Utilitilies - zbynek-legal-format command line utility

Command line utility to manipulate legal files.


## Usage

```
zbynek-legal-format [options] subcommand [options] arguments
```


### join-exhibit

```
zbynek-legal-format -o output.pdf -l exhibit-inputs.tsv -k 2023-Dec-07 join-exhibit -a start-page
```

The command joins exhibit documents specified in exhibit-inputs.tsv into single
file, adds page numbers and updates the csv file with exhibits codes and their
page numbers.

The base page and base exhibit id are taken from the `BASE` entry unless specified on command line.


### update-checksum

```
zbynek-legal-format -l exhibit-inputs.tsv update-checksum
```

The command calculates and updates SHA256 sum on files provided in `-l` option,
updating the `SHA256` column in the file.


### sync-files

```
zbynek-legal-format -l exhibit-inputs.tsv sync-files
```

Downloads the files from remote storage locally


### add-page-numbers

```
zbynek-legal-format -o output.pdf add-page-numbers -a start-page -p page,page-page,... -f index,index-index,... input1.pdf input2.pdf ...
```

The command joins input files into output.pdf, adding page numbers into each
page specified in `-p` parameter (starting at `-a` argument) or `-f` parameter
(starting at `1`).  If no `-p` or `-f` options are provided, it marks all
pages.

### General Options

- `-o output-file` : output file name
- `-l table-file` : contains the list of exhibits, can be either TSV or XLSX file.
- - The first row acts as a header, with the following recognized fields:
- - - `Name` : name of the file (without pdf suffix)
- - - `Media` : name of media file
- - - `SHA256` : SHA256 checksum of file Name
- - - `Media SHA256` : checksum of file Medai
- - - `Sworn Pos` : position of sworn stamp on the page (default is top middle)
- - - `hearing-id Exh` : column with exhibit id  - any non-blank, updated automatically when generating exhibits
- - - `hearing-id Pg` : column with exhibit page number, updated automatically when generating exhibits
- - The second row (if present and marked as `BASE` in `Name` column) contains the base values for exhibits, such as first Exhibit Id and first Exhibit Page
- `-k table-key` : name of the column identifying the file set to operate on, suffixed by `Exh` and `Pg`


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
