package com.alibaba.jvm.util.classnames;

import com.alibaba.jvm.util.DragonwellUtils;
import jdk.internal.org.xml.sax.Attributes;
import jdk.internal.org.xml.sax.InputSource;
import jdk.internal.org.xml.sax.helpers.DefaultHandler;

import jdk.internal.util.xml.impl.SAXParserImpl;

import java.io.*;
import java.lang.reflect.Field;
import java.net.URL;
import java.net.URLClassLoader;
import java.security.PrivilegedAction;
import java.util.*;
import java.util.stream.Collectors;

public class ClassLoaderXMLParser {

    // classloader signature
    static String xmlPath;

    static {
        Properties p = java.security.AccessController.doPrivileged(
                (PrivilegedAction<Properties>) System::getProperties
        );

        xmlPath = p.getProperty("com.alibaba.util.xmlconfig");
        if (xmlPath != null && !new File(xmlPath).isFile()) {
            System.err.println("[Warning] Invalid xmlconfig path: " + xmlPath + ". Use the default configuration instead.");
            xmlPath = null;
        }
    }

    protected static class ClassLoaderNameConfig {
        private String fieldName;
        private boolean appendJarPathsAsName;
        private String uniqueName;

        public ClassLoaderNameConfig(String field, boolean appendJarPathsAsName, String uniqueName) {
            this.fieldName = field;
            this.appendJarPathsAsName = appendJarPathsAsName;
            this.uniqueName = uniqueName;
        }

        public String getFieldName() {
            return fieldName;
        }

        public void setFieldName(String fieldName) {
            this.fieldName = fieldName;
        }

        public boolean isAppendJarPathsAsName() {
            return appendJarPathsAsName;
        }

        public void setAppendJarPathsAsName(boolean appendJarPathsAsName) {
            this.appendJarPathsAsName = appendJarPathsAsName;
        }

        public String getUniqueName() {
            return uniqueName;
        }

        public void setUniqueName(String uniqueName) {
            this.uniqueName = uniqueName;
        }
    }

    private static final class XMLParserHandler extends DefaultHandler {

        private static final String CLASSLOADERS = "classloaders";
        private static final String CLASSLOADER = "classloader";
        private static final String KLASS = "klass";
        private static final String FIELD_NAME = "fieldName";
        private static final String APPEND_JAR_PATHS_AS_NAME = "appendJarPathsAsName";
        private static final String UNIQUE_NAME = "uniqueName";

        private String klass;
        private String fieldName;
        private boolean appendJarPathsAsName;
        private String uniqueName;

        private String characters;

        private static XMLParserHandler singleton = new XMLParserHandler();
        private XMLParserHandler() {}
        static XMLParserHandler getSingleton() { return singleton; }

        @Override
        public void startElement(String uri, String localName, String qName, Attributes attributes) {}

        @Override
        public void characters(char[] ch, int start, int length) {
            characters = new String(ch, start, length).trim();
            characters = characters.isEmpty() ? null : characters;
        }

        @Override
        public void endElement(String uri, String localName, String qName) {
            switch (qName) {
                case CLASSLOADERS:
                    break;
                case CLASSLOADER:
                    // validation
                    if (klass == null) {
                        throw new NullPointerException("klass shouldn't be null");
                    }
                    if (fieldName == null && !appendJarPathsAsName && uniqueName == null) {
                        throw new NullPointerException("klass " + klass + " has no name specified, please check");
                    }
                    // put into map
                    ClassLoaderNameConfig cfg = configs.put(klass, new ClassLoaderNameConfig(fieldName, appendJarPathsAsName, uniqueName));
                    if (cfg != null) {
                        throw new IllegalArgumentException("klass " + klass + " should only have one <classloader> element!");
                    }
                    // reset
                    klass = fieldName = uniqueName = null;
                    appendJarPathsAsName = false;
                    break;
                case KLASS:
                    klass = characters;
                    break;
                case FIELD_NAME:
                    fieldName = characters;
                    break;
                case APPEND_JAR_PATHS_AS_NAME:
                    appendJarPathsAsName = Boolean.parseBoolean(characters);
                    break;
                case UNIQUE_NAME:
                    uniqueName = characters;
                    break;
            }
        }

    }

    private static final int MAXIMUM_FILE_SIZE = 1024 * 1024;

    protected static Map<String, ClassLoaderNameConfig> configs = new HashMap<>();

    private static String readContent(Reader r) throws IOException {
        CharArrayWriter writer = new CharArrayWriter(1024);
        int count = 0;
        int ch;
        while ((ch = r.read()) != -1) {
            writer.write(ch);
            count++;
            if (count >= MAXIMUM_FILE_SIZE) {
                throw new IOException("Presets with more than " + MAXIMUM_FILE_SIZE + " characters can't be read.");
            }
        }
        return new String(writer.toCharArray());
    }

    protected static void parse() throws Exception {
        SAXParserImpl parser = new SAXParserImpl();
        InputStream is;
        if (xmlPath == null) {
            is = ClassLoaderXMLParser.class.getResourceAsStream("/classloader-config.xml");
        } else {
            is = new FileInputStream(xmlPath);
        }
        Reader reader = new BufferedReader(new InputStreamReader(is));
        CharArrayReader r = new CharArrayReader(readContent(reader).toCharArray());
        parser.parse(new InputSource(r), XMLParserHandler.getSingleton());
    }

    private static boolean PARSED = false;

    private static String getJarPathsAsClassLoaderName(URLClassLoader u) {
        URL[] urls = u.getURLs();
        List<String> urlList = Arrays.stream(urls)
                .filter(url -> url.getPath().endsWith("jar"))
                .map(URL::getPath)
                .sorted()
                .collect(Collectors.toList());
        StringBuffer buffer = new StringBuffer();
        buffer.append(u.getClass().getName()).append("@");    // name:  "URLClassLoader@..."
        for (String s : urlList) {
            buffer.append(s);
        }
        return buffer.toString();
    }

    private static String generateIdentifier(ClassLoader loader) throws Exception {
        if (!PARSED) {
            ClassLoaderXMLParser.parse();
            PARSED = true;
        }

        String klassName = loader.getClass().getName();
        String name;
        ClassLoaderXMLParser.ClassLoaderNameConfig cfg = ClassLoaderXMLParser.configs.get(klassName);
        if (cfg == null) {
            return null;
        } else {
            boolean appendJarPathsAsName = cfg.isAppendJarPathsAsName();
            String fieldName = cfg.getFieldName();
            String uniqueName = cfg.getUniqueName();

            // combine
            StringBuilder sb = new StringBuilder();
            sb.append(klassName);

            if (appendJarPathsAsName) {
                if (!URLClassLoader.class.isAssignableFrom(loader.getClass())) {
                    // not a URLClassLoader, we cannot add JarPath
                    if (fieldName == null && uniqueName == null) {
                        throw new NullPointerException("klass " + loader + " cannot appendJarPathsAsName because it's not an URLClassLoader!");
                    }
                    // ignore the `appendJarPathsAsName`.
                } else {
                    sb.append("@").append(getJarPathsAsClassLoaderName((URLClassLoader)loader));
                }
            }
            if (fieldName != null) {
                Field f = loader.getClass().getDeclaredField(fieldName);
                if (f.getType() != String.class) {
                    System.err.println("[Warning] ClassLoader registering: klass " + loader + "'s <fieldName> field should be a java.lang.String type currently. Now using toString()");
                }
                f.setAccessible(true);
                Object targetFieldValue = f.get(loader);
                f.setAccessible(false);
                sb.append("@").append(targetFieldValue);
            }
            if (uniqueName != null) {
                sb.append("@").append(uniqueName);
            }

            name = sb.toString();
        }
        return name;
    }

    public static void registerClassLoader(ClassLoader loader) throws Exception {
        String identifier = generateIdentifier(loader);

        if (identifier != null) {
            // register
            try {
                DragonwellUtils.registerClassLoader.invoke(null, loader, identifier);
                // log
                System.out.println("[Name] Register [" + loader + "] as [" + identifier + "]: "
                        + "hash [" + Integer.toHexString((int) DragonwellUtils.calculateSignatureForName.invoke(null, identifier)) + "]");
            } catch (Exception e) {
                e.printStackTrace();  // only print the Exception: without registering this loader.
            }
        }
    }

}