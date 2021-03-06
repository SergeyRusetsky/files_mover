package com.diana.parser;

import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.w3c.dom.Node;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.*;
import java.util.ArrayList;

public class FileMover {
    private File targetFolder;
    private File sourceFolder;
    private XMLParser parser;
    private File customLabelsFile;
    private ArrayList<String> filesPath;
    private ArrayList<String> labels;

    public FileMover(String sourceFolderPath, String targetFolderPath, XMLParser parser) throws Exception {
        this.sourceFolder = new File(sourceFolderPath);
        this.targetFolder = new File(targetFolderPath);
        this.customLabelsFile = new File(this.sourceFolder.getPath()+ "\\labels\\CustomLabels.labels");


        if (!this.sourceFolder.exists()) {
            throw new Exception(String.format("Folder %s doesn't exist.", this.sourceFolder.getAbsolutePath()));
        }
        if (!this.targetFolder.exists()) {
            createFolder(this.targetFolder.getPath());
        }
        this.parser = parser;
    }

    public void invoke() throws IOException, SAXException, ParserConfigurationException {
        this.filesPath = parser.getFoldersMapping();
        if (this.filesPath.isEmpty()) {
            System.out.println("No files to copy");
            return;
        }

        System.out.println("\nStarted copying ...");
        cleanFolder(this.targetFolder);

        copyCustomLablesFile();

        for (File sourceFile : this.sourceFolder.listFiles()) {
            if (!sourceFile.isDirectory()) {
                continue;
            }
            copyFiles(sourceFile.listFiles(), sourceFile.getName());
        }

        copyFile(parser.getXMLFIle(), this.targetFolder );

        System.out.println("Completed copying ...\n");
    }

    private void copyCustomLablesFile() {
        try {
            labels = new ArrayList<String>();
            for (String filePath : this.filesPath) {
                if (filePath.startsWith("CustomLabel/")) {
                    labels.add(filePath.split("/")[1]);
                }
            }
            if (labels.size() == 0) {
                return;
            }

            String labelsFolderPath = this.targetFolder.getPath() + "\\" +  "labels";
            File labelsFolder = new File(labelsFolderPath);
            if (!labelsFolder.exists()){
                createFolder(labelsFolderPath);
            }

            //--- start copy labels ---
            DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
            DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
            Document docLabels = dBuilder.parse(this.customLabelsFile);
            docLabels.getDocumentElement().normalize();

            NodeList labelsList = docLabels.getElementsByTagName("labels");
            int countOfLabels = labelsList.getLength();

            Document newDocLabels = dBuilder.newDocument();
            Node rootElement = newDocLabels.importNode((Element)docLabels.getElementsByTagName("CustomLabels").item(0), false);
            newDocLabels.appendChild(rootElement);

            for (int i = 0; i < countOfLabels; i++) {
                Node label = labelsList.item(i);
                if (label.getNodeType() == Node.ELEMENT_NODE) {
                    boolean isLabelFromTheList = checkLabel(labels, getValue("fullName", (Element)label));
                    if (isLabelFromTheList) {
                        //System.out.println(">>> " + getValue("fullName", (Element)label));
                        rootElement.appendChild(newDocLabels.importNode((Element)label, true));
                    }
                }
            }

            TransformerFactory transformerFactory = TransformerFactory.newInstance();
            Transformer transformer = transformerFactory.newTransformer();
            DOMSource sourceDoc = new DOMSource(newDocLabels);
            StreamResult resultDoc = new StreamResult(new File(labelsFolderPath + "\\CustomLabels.labels"));
            transformer.transform(sourceDoc, resultDoc);
            //--- end copy labels ---

            //copyFile(this.customLabelsFile, labelsFolder);
        } catch (Exception e) {
            System.out.println(e.getMessage());
        }
    }

    private static String getValue(String tag, Element element, int elementIndex) {
        NodeList nodes = element.getElementsByTagName(tag).item(elementIndex).getChildNodes();
        Node node = (Node) nodes.item(0);
        return node.getNodeValue();
    }

    private static String getValue(String tag, Element element) {
        return getValue(tag, element, 0);
    }

    private static boolean checkLabel(ArrayList<String> labels, String labelToCheck) {
        boolean result = false;
        for (String label : labels) {
            if (label.equals(labelToCheck)) {
                result = true;
                break;
            }
        }
        return result;
    }

    @Override
    public String toString() {
        String str = "";
        for (String filePath : this.filesPath) {
            str += filePath + "\n";
        }
        return str;
    }

    private void cleanFolder(File folder) throws IOException {
        if (folder.listFiles() == null) {
            return;
        }
        for (File file : folder.listFiles()) {
            if (file.isDirectory()) {
                cleanFolder(file);
            }
            file.delete();
            //System.out.println(String.format("%s is deleted.", file.getAbsolutePath()));
        }
    }

    private void createFolder(String newFolderPath) {
        File newFolder = new File(newFolderPath);
        if (!newFolder.exists() && newFolder.mkdirs()) {
            System.out.println(String.format("Folder created: %s.", newFolderPath));
        }
    }

    private String getFileNameWithoutExtension(String nameWithExtension) {
        String pathToTargetFile = nameWithExtension;
        if (pathToTargetFile.endsWith("-meta.xml")) {
            pathToTargetFile = pathToTargetFile.substring(0, pathToTargetFile.lastIndexOf("-meta.xml"));
        }

        if (pathToTargetFile.contains(".")) {
            pathToTargetFile = pathToTargetFile.substring(0, pathToTargetFile.lastIndexOf("."));
        }
        // QuickFixForAura
        if (pathToTargetFile.contains("aura"))   {
            pathToTargetFile = pathToTargetFile.substring(0, pathToTargetFile.lastIndexOf("/"));
        }

        return pathToTargetFile;
    }

    private void copyFiles(File[] sourceFiles, String targetFolderName) throws IOException {
        for (File fileFrom : sourceFiles) {
            if (fileFrom.isDirectory()) {
                String newFolderName = fileFrom.getParentFile().getName() + "/" + fileFrom.getName();
                copyFiles(fileFrom.listFiles(), newFolderName);
            } else {
                String nameWithExtension = targetFolderName + "/" + fileFrom.getName();
                if (!this.filesPath.contains(getFileNameWithoutExtension(nameWithExtension))) {
                    continue;
                }

                createFolder(this.targetFolder.getAbsolutePath() + "/" + targetFolderName);
                copyFile(nameWithExtension);
            }
        }
    }

    private void copyFile(String sourceFileName, String targetFileName) throws IOException {
        try (
                InputStream inputStream = new FileInputStream(sourceFileName);
                OutputStream outputStream = new FileOutputStream(targetFileName);
                BufferedInputStream bufferedInputStream = new BufferedInputStream(inputStream);
                BufferedOutputStream bufferedOutputStream = new BufferedOutputStream(outputStream)
        ) {
            boolean eof = false;
            while (!eof){
                int intToByte = bufferedInputStream.read();
                if (intToByte != -1) {
                    byte currentByte = (byte) intToByte;
                    bufferedOutputStream.write(currentByte);
                } else {
                    eof = true;
                }
            }
        } catch (IOException ex) {
            throw new IOException(String.format("Failed to copy %s to %s.", sourceFileName, targetFileName));
        } catch (Exception e) {
            System.out.println(e.getMessage());
        }
    }

    private void copyFile(File fileToCopy, File targetFolder) throws IOException {
        String sourceFileName = fileToCopy.getPath();
        String targetFileName = targetFolder.getPath() + "/" + fileToCopy.getName();
        copyFile(sourceFileName, targetFileName);
    }

    private void copyTranslationsFile(String fileName){
        try{
            String translationsSourceFilePath = this.sourceFolder.getPath()+ "\\" + fileName;
            String translationsTargetFilePath = this.targetFolder.getPath()+ "\\" + fileName;
            File translationsFile = new File(translationsSourceFilePath);

            //--- start copy translations ---
            DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
            DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
            Document docTranslations = dBuilder.parse(translationsFile);
            docTranslations.getDocumentElement().normalize();

            NodeList translationsList = docTranslations.getElementsByTagName("customLabels");
            int countOfTranslations = translationsList.getLength();

            Document newDocLabels = dBuilder.newDocument();
            Node rootElement = newDocLabels.importNode((Element)docTranslations.getElementsByTagName("Translations").item(0), false);
            newDocLabels.appendChild(rootElement);

            for (int i = 0; i < countOfTranslations; i++) {
                Node translation = translationsList.item(i);
                if (translation.getNodeType() == Node.ELEMENT_NODE) {
                    boolean isLabelFromTheList = checkLabel(labels, getValue("name", (Element)translation));
                    if (isLabelFromTheList) {
                        rootElement.appendChild(newDocLabels.importNode((Element)translation, true));
                    }
                }
            }

            TransformerFactory transformerFactory = TransformerFactory.newInstance();
            Transformer transformer = transformerFactory.newTransformer();
            DOMSource sourceDoc = new DOMSource(newDocLabels);
            StreamResult resultDoc = new StreamResult(new File(translationsTargetFilePath));
            transformer.transform(sourceDoc, resultDoc);
            //--- end copy translations ---
        } catch (Exception e) {
            System.out.println(e.getMessage());
            System.out.println(e.getStackTrace());
        }
    }

    private void copyFile(String fileName) throws IOException {
        if (fileName.startsWith("translations/") && labels != null && labels.size() > 0) {
            copyTranslationsFile(fileName);
        } else {
            String sourceFileName = this.sourceFolder.getAbsolutePath() + "/" + fileName;
            String targetFileName = this.targetFolder.getAbsolutePath() + "/" + fileName;

            copyFile(sourceFileName, targetFileName);
        }
    }
}
