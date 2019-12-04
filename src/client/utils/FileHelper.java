package client.utils;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;

public final class FileHelper {
  private String destinationFolder;

  public FileHelper(String destinationFolder) {
    this.destinationFolder = destinationFolder;
  }

  public ArrayList<ValidFile> getFiles(String... paths) throws IOException {
    ArrayList<ValidFile> list = new ArrayList<>();

    for (String path : paths)
      list.add(getFile(path));

    return list;
  }

  private ValidFile getFile(String path) throws IOException {
    ValidFile file = new ValidFile(path);

    if (!isFileNameValid(file))
      throw new IOException("File " + path + " does not have a valid name.");

    // Check file exists and is not a directory
    if (!file.exists() || file.isDirectory())
      throw new IOException("File " + path + " does not exist or is a directory.");

    return file;
  }

  public byte[] readFile(ValidFile validFile) throws IOException {
    return Files.readAllBytes(Paths.get(validFile.getPath()));
  }

  public synchronized void writeFile(String fileName, byte[] data) throws IOException {
    Path filePath = Paths.get(destinationFolder + "/" + fileName);

    // Check destination valid
    if (Files.isDirectory(filePath) || !Files.isWritable(filePath))
      throw new IOException("Invalid destination for file: " + filePath.toString());

    // Delete previous file
    if (Files.exists(filePath))
      Files.delete(filePath);

    // write the file
    Files.write(filePath, data);
  }

  public String getFileSpec(ValidFile... validFiles) {
    StringBuilder stringBuilder = new StringBuilder();

    // Build spec for all files
    for (int i = 0; i < validFiles.length; i++) {
      ValidFile validFile = validFiles[i];

      stringBuilder.append(validFile.getName());
      stringBuilder.append(" ");
      stringBuilder.append(validFile.length());

      if (i < validFiles.length - 1)
        stringBuilder.append(", ");
    }

    return stringBuilder.toString();
  }

  /*
    UTILS
  */
  private boolean isFileNameValid(File file) {
    // Get name and extension and check they are valid
    String[] trimmedName = file.getName().split("\\.");

    if (trimmedName.length != 2)
      return false;

    if (trimmedName[0].length() == 0 || trimmedName[1].length() == 0)
      return false;

    return true;
  }
}
