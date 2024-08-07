# Zbynek Legal Utilitilies - zbynek-legal-format command line utility

Command line utility to manipulate legal files.


## Download

- https://github.com/kvr000/zbynek-legal-util/releases/download/master/zbynek-legal-format
- https://github.com/kvr000/zbynek-legal-util/releases/tag/master


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

#### Options

- `--code id` : prints supporting code, omit the id for list
- `--base base-document` : base document to start with
- `-a page-number` : first page number (default 1)
- `-s sworn-text` : sworn stamp text, can contain placeholders in {key} form
- `--sa` : set sworn stamp text to affirmed, can contain placeholders in {key} form
- `--ss` : set sworn stamp text to sworn, can contain placeholders in {key} form
- `-t key=value` : substituted values for templates Pg
- `--tt` : read substituted values from Text sheet from index file
- `--ta` : read substituted values from Text sheet from index file and date from first -k option (default)
- `--tn` : do not read substituted values from Text sheet from index file
- `--extract what (multi)` : extracts only subset of pages, possible values: first (first page) last (last page) exhibit-first (exhibit first pages) single (single page) odd (odd-even pair)
- `-i` : ignore errors, such as file not found

#### Configuration

`join-exhibit` command reads the following configuration from index file:

Sheet `config`, header `key` and `value` :
- `exhibitTemplateName` : can be either `swear` or `affirm`
- `exhibitTemplateText` : contains full text for Exhibit sworn/affirmed statement.  The `{id}` mark the text substitions

Sheet `text`, header `key` and `value` :
- `name` : name of applicant
- `date` : date of submission
- `province` : province name
- ... : anything else that is contained in Exhibit Template (only above and dynamic `exhibitId` are supported by default templates)


### update-checksum

```
zbynek-legal-format -l exhibit-inputs.tsv update-checksum
```

The command calculates and updates SHA256 sum on files provided in `-l` option,
updating the `SHA256` and `Media SHA256` columns in the file.


### sync-files

```
zbynek-legal-format -l exhibit-inputs.tsv sync-files
```

Downloads the files from remote storage locally


### zip

```
zbynek-legal-format -l exhibit-inputs.tsv -o o.zip zip -s 10M
```

Compresses all files into zip archive.  `-s size` splits into zip parts, `-a size` splits into complete zip archives.


### add-page-numbers

```
zbynek-legal-format -o output.pdf add-page-numbers -a start-page -p page,page-page,... -f index,index-index,... input1.pdf input2.pdf ...
```

The command joins input files into output.pdf, adding page numbers into each
page specified in `-p` parameter (starting at `-a` argument) or `-f` parameter
(starting at `1`).  If no `-p` or `-f` options are provided, it marks all
pages.


### pdf-join

```
zbynek-legal-format -o output.pdf pdf-join -a first-page -p render-position -f page-format-pattern input1.pdf input2.pdf ...
```

The command joins input files into a destination and optionally adds page numbers.

#### Options

- `--decompress` : decompress input files
- `--append` : append to output
- `--skip-first` : do not modify first file (typically when appending)
- `-a start-page` : add page numbers, starting with this parameter value
- `-p x,y` : position to render page number to (range 0 - 1)
- `-f page-number-pattern` : page number pattern, such as Page %02d


### pdf-split

```
zbynek-legal-format -o output.pdf pdf-split -s max-size -p max-pages -g page-group-size input1.pdf input2.pdf ...
```

The command joins input files into a destination and split into output-0xyz.pdf files based on max-size or max page length.

#### Options

- `-s max-size[BKMG]` : max file size
- `-p page-count` : max number of pages
- `-g group-size` : number of pages to group together (default is 2)


### pdf-replace

The command replaces pages in output with provided pages from input.

```
zbynek-legal-format -o main.pdf pdf-replace -i input-file -m destination=source -m destination=source ...
```

#### Options

- `-i input-file` : input file for operation
- `-m destinationPage[=sourcePage]` : moves sourcePage to destinationPage (same page if sourcePage not provided)
- `-a destinationPage` : adds new blank page
- `-d start-[end]` : remove pages
- `--title document-title` : sets document title
- `--subject document-subject` : sets document subject
- `--author document-author` : sets document author
- `--replace-meta` : replace meta to original document (default is to keep)
- `--meta-from document` : copy all meta from specified document
- `--delete-all-meta` : delete all meta fields
- `--delete-meta key` : delete meta field named key


### pdf-meta

The command prints PDF meta fields.

```
zbynek-legal-format -o main.pdf pdf-meta
```

#### Options

N/A


### pdf-decompress

The command internally decompresses PDF file.

```
zbynek-legal-format -o output.pdf pdf-decompress input.pdf
```

#### Options

N/A


### pdf-empty

The command creates empty (zero pages) PDF file.

```
zbynek-legal-format -o output.pdf pdf-empty
```

#### Options

N/A


### pdf-resize

The command resizes PDF files made from images.

```
zbynek-legal-format -o output.pdf pdf-resize [-q quality ] [ -s scale ] input.pdf
```

It is better to not specify scale factor as it's taken from page size, not the image size.

#### Options

`-w target-width` : target page width (or height / shorter size if rotated)
`-s scale` : page scale factor
`-q quality` : compression quality


### merge-ink

The command merges look-like-ink parts from `scanned-file` to original digital `base-file`, maintaining the digital
quality.

```
zbynek-legal-format -o output.pdf merge-ink -b base-file -i scanned-file
```

#### Options

- `-b base-file` : original digital file
- `-i ink-file` : printed file with ink text to copy
- `-f flip-till` : flip source image vertically till this page


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

Author: Zbynek Vyskovsky

Feel free to contact me at kvr000@gmail.com  and http://github.com/kvr000/ and http://github.com/kvr000/zbynek-legal-util/

LinkedIn: https://www.linkedin.com/in/zbynek-vyskovsky/

[Apache License]: http://www.apache.org/licenses/LICENSE-2.0
