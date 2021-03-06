package aeminium.gpu.templates;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.HashMap;

public class Template {
	private String source;

	public Template(TemplateWrapper t) {
		readStream(t.toString(), t.getReader());
	}

	public Template(File f) {
		readStream(f);
	}

	public Template(String source) {
		this.source = source;
	}

	public void readStream(File f) {
		try {
			readStream(f.getAbsolutePath(),
					new BufferedReader(new FileReader(f.getAbsoluteFile())));
		} catch (FileNotFoundException e) {
			e.printStackTrace();
			System.exit(1);
		}
	}

	public void readStream(String fname, BufferedReader in) {
		try {
			StringBuilder builder = new StringBuilder();
			String str;
			while ((str = in.readLine()) != null) {
				builder.append(str + "\n");
			}
			in.close();
			source = builder.toString();
		} catch (IOException e) {
			System.out.println("Failed loading template " + fname);
		}
	}

	public String apply(HashMap<String, String> mapping) {
		if (source == null) {
			System.out.println("No source given to template.");
			System.exit(1);
		}
		String newSource = source;
		for (String key : mapping.keySet()) {
			if (mapping.get(key) == null) {
				mapping.put(key, "");
			}
			newSource = newSource.replace((CharSequence) "{{" + key + "}}",
					mapping.get(key));
		}

		return newSource;
	}

}
