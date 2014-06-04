package tabletop2.util;

import java.io.File;

public class FileUtils {
    
	public static void deleteRecursively(File file) {
    	if (!file.exists()) {
    		return;
    	}
    	if (!file.isDirectory()) {
    		file.delete();
    		return;
    	}
    	for (File f : file.listFiles()) {
    		deleteRecursively(f);
    	}
    	file.delete();
    }
}
