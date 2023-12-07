package pt.haslab.mulletbench;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

public class ResourceAccess {

    public static BufferedReader getResourceBufferedReader(final String fileName) throws IOException {
        InputStream fileStream = ResourceAccess.class.getClassLoader().getResourceAsStream(fileName);

        return new BufferedReader(new InputStreamReader(fileStream));
    }

    public static BufferedReader getFileBufferedReader(final String filePath, final Class c) throws FileNotFoundException {
        String mainPath = new File(c.getProtectionDomain().getCodeSource().getLocation().getPath()).getParent();
        String fullPath = mainPath + filePath;
        return new BufferedReader(new InputStreamReader(new FileInputStream(fullPath)));
    }
}