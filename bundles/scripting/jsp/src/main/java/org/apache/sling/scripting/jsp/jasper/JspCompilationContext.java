/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.sling.scripting.jsp.jasper;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import javax.servlet.ServletContext;
import javax.servlet.jsp.tagext.TagInfo;

import org.apache.sling.scripting.jsp.jasper.compiler.Compiler;
import org.apache.sling.scripting.jsp.jasper.compiler.JspRuntimeContext;
import org.apache.sling.scripting.jsp.jasper.compiler.JspUtil;
import org.apache.sling.scripting.jsp.jasper.compiler.Localizer;
import org.apache.sling.scripting.jsp.jasper.compiler.ServletWriter;
import org.apache.sling.scripting.jsp.jasper.servlet.JspServletWrapper;

/**
 * A place holder for various things that are used through out the JSP
 * engine. This is a per-request/per-context data structure. Some of
 * the instance variables are set at different points.
 *
 * Most of the path-related stuff is here - mangling names, versions, dirs,
 * loading resources and dealing with uris.
 *
 * @author Anil K. Vijendran
 * @author Harish Prabandham
 * @author Pierre Delisle
 * @author Costin Manolache
 * @author Kin-man Chung
 */
public class JspCompilationContext {

    protected org.apache.juli.logging.Log log =
        org.apache.juli.logging.LogFactory.getLog(JspCompilationContext.class);

    protected Map<String, URL> tagFileJarUrls;
    protected boolean isPackagedTagFile;

    protected String className;
    protected String jspUri;
    protected boolean isErrPage;
    protected String basePackageName;
    protected String derivedPackageName;
    protected String servletJavaFileName;
    protected String javaPath;
    protected String classFileName;
    protected String contentType;
    protected ServletWriter writer;
    protected Options options;
    protected JspServletWrapper jsw;
    protected Compiler jspCompiler;
    protected String classPath;

    protected String baseURI;
    protected String outputDir;
    protected ServletContext context;

    protected JspRuntimeContext rctxt;

    protected int removed = 0;

    protected URL baseUrl;
    protected Class<?> servletClass;

    protected boolean isTagFile;
    protected boolean protoTypeMode;
    protected TagInfo tagInfo;
    protected URL tagFileJarUrl;

    // jspURI _must_ be relative to the context
    public JspCompilationContext(String jspUri,
                                 boolean isErrPage,
                                 Options options,
                                 ServletContext context,
                                 JspServletWrapper jsw,
                                 JspRuntimeContext rctxt) {

        this.jspUri = canonicalURI(jspUri);
        this.isErrPage = isErrPage;
        this.options = options;
        this.jsw = jsw;
        this.context = context;

        this.baseURI = jspUri.substring(0, jspUri.lastIndexOf('/') + 1);
        // hack fix for resolveRelativeURI
        if (baseURI == null) {
            baseURI = "/";
        } else if (baseURI.charAt(0) != '/') {
            // strip the basde slash since it will be combined with the
            // uriBase to generate a file
            baseURI = "/" + baseURI;
        }
        if (baseURI.charAt(baseURI.length() - 1) != '/') {
            baseURI += '/';
        }

        this.rctxt = rctxt;
        this.tagFileJarUrls = new HashMap<String, URL>();
        this.basePackageName = Constants.JSP_PACKAGE_NAME;
    }

    public JspCompilationContext(String tagfile,
                                 TagInfo tagInfo,
                                 Options options,
                                 ServletContext context,
                                 JspServletWrapper jsw,
                                 JspRuntimeContext rctxt,
                                 URL tagFileJarUrl) {
        this(tagfile, false, options, context, jsw, rctxt);
        this.isTagFile = true;
        this.tagInfo = tagInfo;
        this.tagFileJarUrl = tagFileJarUrl;
        if (tagFileJarUrl != null) {
            isPackagedTagFile = true;
        }
    }

    /**
     * What class loader to use for loading classes while compiling
     * this JSP?
     */
    public ClassLoader getClassLoader() {
        return getRuntimeContext().getIOProvider().getClassLoader();
    }

    /** ---------- Input/Output  ---------- */

    /**
     * Creates an output stream to the given file
     * @param fileName The path to the file to write to
     * @return The OutputStream to the file
     * @throws IOException If an error occurrs creating the output stream.
     */
    public OutputStream getOutputStream(String fileName) throws IOException {
        return getRuntimeContext().getIOProvider().getOutputStream(fileName);
    }

    /**
     * Creates an input stream from the given file
     * @param fileName The path to the file to read from
     * @return The InputStream from the file
     * @throws FileNotFoundException If the file cannot be found
     * @throws IOException If an error occurrs creating the intput stream.
     */
    public InputStream getInputStream(String fileName)
            throws FileNotFoundException, IOException {
        return getRuntimeContext().getIOProvider().getInputStream(fileName);
    }

    /**
     * Removes a (generated) file.
     * @param fileName The path to the file to remove.
     * @return <code>true</code> if the file has been removed.
     */
    public boolean delete(String fileName) {
        return getRuntimeContext().getIOProvider().delete(fileName);
    }

    /**
     * Renams then old file to the new file
     * @return <code>true</code> if the file has been renamed
     *
     */
    public boolean rename(String oldFileName, String newFileName) {
        return getRuntimeContext().getIOProvider().rename(oldFileName, newFileName);
    }

    /**
     * The output directory to generate code into.  The output directory
     * is make up of the scratch directory, which is provide in Options,
     * plus the directory derived from the package name.
     */
    public String getOutputDir() {
	if (outputDir == null) {
	    createOutputDir();
	}

        return outputDir;
    }

    /**
     * Create a "Compiler" object based on some init param data. This
     * is not done yet. Right now we're just hardcoding the actual
     * compilers that are created.
     */
    public Compiler createCompiler() throws JasperException {
        if (jspCompiler != null ) {
            return jspCompiler;
        }
        jspCompiler = null;
        if (options.getCompilerClassName() != null) {
            jspCompiler = createCompiler(options.getCompilerClassName());
        } else {
            if (options.getCompiler() == null) {
                jspCompiler = createCompiler("org.apache.sling.scripting.jsp.jasper.compiler.JDTCompiler");
                if (jspCompiler == null) {
                    jspCompiler = createCompiler("org.apache.sling.scripting.jsp.jasper.compiler.AntCompiler");
                }
            } else {
                jspCompiler = createCompiler("org.apache.sling.scripting.jsp.jasper.compiler.AntCompiler");
                if (jspCompiler == null) {
                    jspCompiler = createCompiler("org.apache.sling.scripting.jsp.jasper.compiler.JDTCompiler");
                }
            }
        }
        if (jspCompiler == null) {
            throw new IllegalStateException(Localizer.getMessage("jsp.error.compiler"));
        }
        jspCompiler.init(this, jsw);
        return jspCompiler;
    }

    protected Compiler createCompiler(String className) {
        Compiler compiler = null;
        try {
            compiler = (Compiler) this.getClass().getClassLoader().loadClass(className).newInstance();
        } catch (InstantiationException e) {
            log.warn(Localizer.getMessage("jsp.error.compiler"), e);
        } catch (IllegalAccessException e) {
            log.warn(Localizer.getMessage("jsp.error.compiler"), e);
        } catch (NoClassDefFoundError e) {
            if (log.isDebugEnabled()) {
                log.debug(Localizer.getMessage("jsp.error.compiler"), e);
            }
        } catch (ClassNotFoundException e) {
            if (log.isDebugEnabled()) {
                log.debug(Localizer.getMessage("jsp.error.compiler"), e);
            }
        }
        return compiler;
    }

    public Compiler getCompiler() {
        return jspCompiler;
    }

    /** ---------- Access resources in the webapp ---------- */

    /**
     * Get the full value of a URI relative to this compilations context
     * uses current file as the base.
     */
    public String resolveRelativeUri(String uri) {
        // sometimes we get uri's massaged from File(String), so check for
        // a root directory deperator char
        if (uri.startsWith("/") || uri.startsWith(File.separator)) {
            return uri;
        }
        return baseURI + uri;
    }

    /**
     * Gets a resource as a stream, relative to the meanings of this
     * context's implementation.
     * @return a null if the resource cannot be found or represented
     *         as an InputStream.
     */
    public java.io.InputStream getResourceAsStream(String res) {
        return context.getResourceAsStream(canonicalURI(res));
    }


    public URL getResource(String res) throws MalformedURLException {
        return context.getResource(canonicalURI(res));
    }

    @SuppressWarnings("unchecked")
    public Set<String> getResourcePaths(String path) {
        return context.getResourcePaths(canonicalURI(path));
    }

    /**
     * Gets the actual path of a URI relative to the context of
     * the compilation.
     */
    public String getRealPath(String path) {
        if (context != null) {
            return context.getRealPath(path);
        }
        return path;
    }

    /**
     * Returns the tag-file-name-to-JAR-file map of this compilation unit,
     * which maps tag file names to the JAR files in which the tag files are
     * packaged.
     *
     * The map is populated when parsing the tag-file elements of the TLDs
     * of any imported taglibs.
     */
    public URL getTagFileJarUrl(String tagFile) {
        return this.tagFileJarUrls.get(tagFile);
    }

    public void setTagFileJarUrl(String tagFile, URL tagFileURL) {
        this.tagFileJarUrls.put(tagFile, tagFileURL);
    }

    /**
     * Returns the JAR file in which the tag file for which this
     * JspCompilationContext was created is packaged, or null if this
     * JspCompilationContext does not correspond to a tag file, or if the
     * corresponding tag file is not packaged in a JAR.
     */
    public URL getTagFileJarUrl() {
        return this.tagFileJarUrl;
    }

    /* ==================== Common implementation ==================== */

    /**
     * Just the class name (does not include package name) of the
     * generated class.
     */
    public String getServletClassName() {

        if (className != null) {
            return className;
        }

        if (isTagFile) {
            className = tagInfo.getTagClassName();
            int lastIndex = className.lastIndexOf('.');
            if (lastIndex != -1) {
                className = className.substring(lastIndex + 1);
            }
        } else {
            int iSep = jspUri.lastIndexOf('/') + 1;
            className = JspUtil.makeJavaIdentifier(jspUri.substring(iSep));
        }
        return className;
    }

    public void setServletClassName(String className) {
        this.className = className;
    }

    /**
     * Path of the JSP URI. Note that this is not a file name. This is
     * the context rooted URI of the JSP file.
     */
    public String getJspFile() {
        return jspUri;
    }

    /**
     * Are we processing something that has been declared as an
     * errorpage?
     */
    public boolean isErrorPage() {
        return isErrPage;
    }

    public void setErrorPage(boolean isErrPage) {
        this.isErrPage = isErrPage;
    }

    public boolean isTagFile() {
        return isTagFile;
    }

    public TagInfo getTagInfo() {
        return tagInfo;
    }

    public void setTagInfo(TagInfo tagi) {
        tagInfo = tagi;
    }

    /**
     * True if we are compiling a tag file in prototype mode.
     * ie we only generate codes with class for the tag handler with empty
     * method bodies.
     */
    public boolean isPrototypeMode() {
        return protoTypeMode;
    }

    public void setPrototypeMode(boolean pm) {
        protoTypeMode = pm;
    }

    /**
     * Package name for the generated class is make up of the base package
     * name, which is user settable, and the derived package name.  The
     * derived package name directly mirrors the file heirachy of the JSP page.
     */
    public String getServletPackageName() {
        if (isTagFile()) {
            String className = tagInfo.getTagClassName();
            int lastIndex = className.lastIndexOf('.');
            String pkgName = "";
            if (lastIndex != -1) {
                pkgName = className.substring(0, lastIndex);
            }
            return pkgName;
        } else if (basePackageName == null || basePackageName.length() == 0) {
            return getDerivedPackageName();
        } else {
            String dPackageName = getDerivedPackageName();
            if (dPackageName.length() == 0) {
                return basePackageName;
            }
            return basePackageName + '.' + getDerivedPackageName();
        }
    }

    protected String getDerivedPackageName() {
        if (derivedPackageName == null) {
            int iSep = jspUri.lastIndexOf('/');
            derivedPackageName = (iSep > 0) ?
                    JspUtil.makeJavaPackage(jspUri.substring(1,iSep)) : "";
        }
        return derivedPackageName;
    }

    /**
     * The package name into which the servlet class is generated.
     */
    public void setServletPackageName(String servletPackageName) {
        this.basePackageName = servletPackageName;
    }

    /**
     * Full path name of the Java file into which the servlet is being
     * generated.
     */
    public String getServletJavaFileName() {
        if (servletJavaFileName == null) {
            servletJavaFileName = getOutputDir() + getServletClassName() + ".java";
        }
        return servletJavaFileName;
    }

    /**
     * Get hold of the Options object for this context.
     */
    public Options getOptions() {
        return options;
    }

    public ServletContext getServletContext() {
        return context;
    }

    public JspRuntimeContext getRuntimeContext() {
        return rctxt;
    }

    /**
     * Path of the Java file relative to the work directory.
     */
    public String getJavaPath() {

        if (javaPath != null) {
            return javaPath;
        }

        if (isTagFile()) {
	    String tagName = tagInfo.getTagClassName();
            javaPath = tagName.replace('.', '/') + ".java";
        } else {
            javaPath = getServletPackageName().replace('.', '/') + '/' +
                       getServletClassName() + ".java";
	}
        return javaPath;
    }

    public String getClassFileName() {
        if (classFileName == null) {
            classFileName = getOutputDir() + getServletClassName() + ".class";
        }
        return classFileName;
    }

    /**
     * Get the content type of this JSP.
     *
     * Content type includes content type and encoding.
     */
    public String getContentType() {
        return contentType;
    }

    public void setContentType(String contentType) {
        this.contentType = contentType;
    }

    /**
     * Where is the servlet being generated?
     */
    public ServletWriter getWriter() {
        return writer;
    }

    public void setWriter(ServletWriter writer) {
        this.writer = writer;
    }

    /**
     * Gets the 'location' of the TLD associated with the given taglib 'uri'.
     *
     * @return An array of two Strings: The first element denotes the real
     * path to the TLD. If the path to the TLD points to a jar file, then the
     * second element denotes the name of the TLD entry in the jar file.
     * Returns null if the given uri is not associated with any tag library
     * 'exposed' in the web application.
     */
    public String[] getTldLocation(String uri) throws JasperException {
        String[] location =
            getOptions().getTldLocationsCache().getLocation(uri);
        return location;
    }

    /**
     * Are we keeping generated code around?
     */
    public boolean keepGenerated() {
        return getOptions().getKeepGenerated();
    }

    // ==================== Removal ====================

    public void incrementRemoved() {
        if (removed == 0 && rctxt != null) {
            rctxt.removeWrapper(jspUri);
        }
        removed++;
    }

    public boolean isRemoved() {
        if (removed > 1 ) {
            return true;
        }
        return false;
    }

    // ==================== Compile and reload ====================

    public void compile() throws JasperException, IOException, FileNotFoundException {
        createCompiler();
        if (isPackagedTagFile || jspCompiler.isOutDated()) {
            try {
                jspCompiler.removeGeneratedFiles();
                jspCompiler.compile();
                jsw.setReload(true);
                jsw.setCompilationException(null);
                this.getRuntimeContext().addJspDependencies(jsw);
            } catch (JasperException ex) {
                // Cache compilation exception
                jsw.setCompilationException(ex);
                throw ex;
            } catch (IOException ioe) {
                JasperException je = new JasperException(
                        Localizer.getMessage("jsp.error.unable.compile"),
                        ioe);
                // Cache compilation exception
                jsw.setCompilationException(je);
                throw ioe;
            } catch (Exception ex) {
                JasperException je = new JasperException(
                            Localizer.getMessage("jsp.error.unable.compile"),
                            ex);
                // Cache compilation exception
                jsw.setCompilationException(je);
                throw je;
            }
        }
    }

    // ==================== Manipulating the class ====================

    public Class<?> load()
        throws JasperException, FileNotFoundException
    {
        try {
            String name;
            if (isTagFile()) {
                name = tagInfo.getTagClassName();
            } else {
                name = getServletPackageName() + "." + getServletClassName();
            }
            servletClass = getClassLoader().loadClass(name);
        } catch (ClassNotFoundException cex) {
            throw new JasperException(Localizer.getMessage("jsp.error.unable.load"),
                                      cex);
        } catch (Exception ex) {
            throw new JasperException(Localizer.getMessage("jsp.error.unable.compile"),
                                      ex);
        }
        removed = 0;
        return servletClass;
    }

    // ==================== protected methods ====================

    static Object outputDirLock = new Object();

    public void checkOutputDir() {
        if (outputDir != null) {
            if (!(new File(outputDir)).exists()) {
                makeOutputDir();
            }
        } else {
            createOutputDir();
        }
    }

    protected boolean makeOutputDir() {
        synchronized(outputDirLock) {
            return getRuntimeContext().getIOProvider().mkdirs(outputDir);
        }
    }

    protected void createOutputDir() {
        String path = null;
        if (isTagFile()) {
	    String tagName = tagInfo.getTagClassName();
            path = tagName.replace('.', '/');
	    path = path.substring(0, path.lastIndexOf('/'));
        } else {
            path = getServletPackageName().replace('.', '/');
	}

            // Append servlet or tag handler path to scratch dir
            try {
                baseUrl = new File(options.getScratchDir()).toURI().toURL();
                outputDir = options.getScratchDir() + File.separator + path + File.separator;
                if (!makeOutputDir()) {
                    throw new IllegalStateException(Localizer.getMessage("jsp.error.outputfolder"));
                }
            } catch (MalformedURLException e) {
                throw new IllegalStateException(Localizer.getMessage("jsp.error.outputfolder"), e);
            }
    }

    protected static final boolean isPathSeparator(char c) {
       return (c == '/' || c == '\\');
    }

    protected static final String canonicalURI(String s) {
       if (s == null) return null;
       StringBuffer result = new StringBuffer();
       final int len = s.length();
       int pos = 0;
       while (pos < len) {
           char c = s.charAt(pos);
           if ( isPathSeparator(c) ) {
               /*
                * multiple path separators.
                * 'foo///bar' -> 'foo/bar'
                */
               while (pos+1 < len && isPathSeparator(s.charAt(pos+1))) {
                   ++pos;
               }

               if (pos+1 < len && s.charAt(pos+1) == '.') {
                   /*
                    * a single dot at the end of the path - we are done.
                    */
                   if (pos+2 >= len) break;

                   switch (s.charAt(pos+2)) {
                       /*
                        * self directory in path
                        * foo/./bar -> foo/bar
                        */
                   case '/':
                   case '\\':
                       pos += 2;
                       continue;

                       /*
                        * two dots in a path: go back one hierarchy.
                        * foo/bar/../baz -> foo/baz
                        */
                   case '.':
                       // only if we have exactly _two_ dots.
                       if (pos+3 < len && isPathSeparator(s.charAt(pos+3))) {
                           pos += 3;
                           int separatorPos = result.length()-1;
                           while (separatorPos >= 0 &&
                                  ! isPathSeparator(result
                                                    .charAt(separatorPos))) {
                               --separatorPos;
                           }
                           if (separatorPos >= 0)
                               result.setLength(separatorPos);
                           continue;
                       }
                   }
               }
           }
           result.append(c);
           ++pos;
       }
       return result.toString();
    }
}

