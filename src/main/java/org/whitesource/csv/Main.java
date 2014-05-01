package org.whitesource.csv;

public class Main {

    public static void main(String[] args) {
        String csvFilePath = args[0];
        WhitesourceCsv whitesourceCsv = new WhitesourceCsv(csvFilePath);
        whitesourceCsv.execute();
    }

}
