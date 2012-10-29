/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.jvnet.ws.wadl.maven;

import java.io.*;
import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.tools.*;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.MultivaluedHashMap;
import javax.ws.rs.core.MultivaluedMap;
import javax.xml.bind.JAXBElement;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugin.testing.AbstractMojoTestCase;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.util.FileUtils;
import org.easymock.classextension.EasyMock;
import static org.fest.reflect.core.Reflection.*;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.jvnet.ws.wadl.matchers.Matchers.contains;
import static org.jvnet.ws.wadl.matchers.Matchers.exists;
import org.w3c.dom.Element;


/**
 *
 * @author gdavison
 */
public abstract class AbstractWadl2JavaMojoTest<ClientType> extends AbstractMojoTestCase {

    
    /**
     * A class to store a canned response to a client request
     */
    
    protected static class CannedResponse
    {
        public final int status;
        public final MultivaluedMap headers;
        public final byte[] content;
        
        public CannedResponse(int status, String contentType, String content)
        {
            this.status = status;
            headers = new MultivaluedHashMap();
            headers.add("Content-Type", contentType);
            try
            {
                this.content = content.getBytes("UTF-8");
            } catch (Exception ex) {
                throw new RuntimeException("Problem converting text", ex);
            }
            
            headers.add("Content-Length", Integer.toString(this.content.length));
            
        }
    }
    
    /**
     * A class that wraps the response for the given implementation
     */
    
    protected interface WrapperResponse
    {
       public URI getURI();
       public String getMethod();
    }
    
    
    
    /**
     * A mock object representing the active project.
     */
    protected MavenProject _project;
    
    /**
     * A mock client that stores all request
     * @throws Exception 
     */
    protected ClientType _client;
    
    /**
     * Store a list of client request
     */
    protected List<WrapperResponse> _requests = new ArrayList<WrapperResponse>();
    
    /**
     * Store a list of canned responses
     */
    protected List<CannedResponse> _cannedResponse = new ArrayList<CannedResponse>();
    
    
    /**
     * A convenience method for getting a link to the resources dir
     *
     * @param subpath
     *            The sub path under the resources directory
     * @return The full path to this file
     */
    public static String getFilePath(String subpath) {
        return getBasedir() + "/src/test/resources/" + subpath;
    }


    /**
     * @return A new instance of the client customised for automated testing
     *  that is of the correct type for this client
     */
    protected abstract ClientType createClient();

    
    @Override
    public void setUp() throws Exception {
        super.setUp();
        _project = EasyMock.createMock(MavenProject.class);
        // Register the package for the stream handler
        System.setProperty("java.protocol.handler.pkgs", "org.jvnet.ws.wadl.maven");
        // Configure a mock click so we can capture any request
        _client = createClient();
        _requests.clear();
        _cannedResponse.clear();
    }

    @Override
    protected void tearDown() throws Exception {
        //compiled code
        super.tearDown();
        _project = null;
        _client = null;
        _requests.clear();
        _cannedResponse.clear();
    }

    
    
    
    
    protected ClassLoader compile(File targetDirectory) throws MalformedURLException {
        // Compile the source

        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        final DiagnosticCollector diagnosticCollector = new DiagnosticCollector();
        StandardJavaFileManager fileManager = compiler.getStandardFileManager(
                diagnosticCollector,
                null, null);

        List<File> files = listFilesRecursively(targetDirectory);

        Iterable<? extends JavaFileObject> compilationUnits1 =
                fileManager.getJavaFileObjectsFromFiles(files);
        boolean success = compiler.getTask(null, fileManager, null, null, null, compilationUnits1).call();
        assertTrue("Compilation failed for some reason", success);

        assertThat(diagnosticCollector.getDiagnostics().size(), equalTo(0));

        // Create an return a stuitable class loader
        //

        return new URLClassLoader(new java.net.URL[]{targetDirectory.toURI().toURL()});
    }

    /**
     * @return a list of all java files under the given directory
     */
    protected List<File> listFilesRecursively(File root) {

        List<File> files = new ArrayList<File>();
        List<File> dirs = new ArrayList<File>();
        dirs.add(root);

        for (int i = 0; i < dirs.size(); i++) {
            File dir = dirs.get(i);

            files.addAll(Arrays.asList(dir.listFiles(new FilenameFilter() {

                public boolean accept(File dir, String name) {
                    return name.endsWith(".java");
                }
            })));

            dirs.addAll(Arrays.asList(dir.listFiles(new FilenameFilter() {

                public boolean accept(File dir, String name) {
                    return new File(dir, name).isDirectory();
                }
            })));
        }

        return files;
    }

    /**
     * A convenience method for getting a configured {@lik Wadl2JavaMojo}
     * instance.
     * 
     * @param pluginXml
     *            The name of the configuration file, configuring the
     *            {@link Wadl2JavaMojo}. The operation will search for a file
     *            named <code>pluginXml</code> in
     *            <code>${basedir}/src/test/plugin-configs/wadl2java</code>.
     * @return A configured instance of the {@link Wadl2JavaMojo}.
     * @throws Exception
     *             If an instance of the {@link Wadl2JavaMojo} cannot be
     *             constructed from the file name passed in.
     */
    protected Wadl2JavaMojo getMojo(String pluginXml) throws Exception {
        return (Wadl2JavaMojo) lookupMojo("generate", getBasedir()
                + "/src/test/plugin-configs/wadl2java/" + pluginXml);
    }
    
    
    /**
     * @return The client class for this implementation
     */
    protected abstract Class getClientClass();

    /**
     * @return The response class for this implementation
     */
    protected abstract Class getResponseClass();

    
    /**
     * @return The generic type class for this implementation
     */
    protected abstract Class getGenericTypeClass();
    
    /**
     * @return A regex to verify that the application is being invoked
     */
    protected abstract String getReturnStatmentRegex();
    
    // 
    // Here are the tests that can be shared between the two different implemntations
    //

    
        /**
     * Tests the case in which a valid wadl file exists.
     */
    public void testValidWadlFiles() throws Exception {
        // Prepare
        Wadl2JavaMojo mojo = getMojo("valid-wadl-config.xml");
        File targetDirectory = (File) getVariableValueFromObject(mojo,
                "targetDirectory");
        if (targetDirectory.exists()) {
            FileUtils.deleteDirectory(targetDirectory);
        }
        setVariableValueToObject(mojo, "project", _project);

        // Record
        _project.addCompileSourceRoot(targetDirectory.getAbsolutePath());

        // Replay
        EasyMock.replay(_project);
        mojo.execute();

        // Verify
        EasyMock.verify(_project);
        assertThat(targetDirectory, exists());
        assertThat(targetDirectory, contains("test"));
        assertThat(targetDirectory, contains("test/ApiSearchYahooCom_NewsSearchServiceV1.java"));
        assertThat(targetDirectory, contains("test/SearchErrorException.java"));
        assertThat(targetDirectory, contains("test/Output.java"));
        assertThat(targetDirectory, contains("test/Type.java"));
        assertThat(targetDirectory, contains("test/Sort.java"));
        assertThat(targetDirectory, contains("yahoo/api/ObjectFactory.java"));
        assertThat(targetDirectory, contains("yahoo/api/Error.java"));
        assertThat(targetDirectory, contains("yahoo/yn/ImageType.java"));
        assertThat(targetDirectory, contains("yahoo/yn/ObjectFactory.java"));
        assertThat(targetDirectory, contains("yahoo/yn/ResultSet.java"));
        assertThat(targetDirectory, contains("yahoo/yn/ResultType.java"));

        // Check that the generated code compiles
        ClassLoader cl = compile(targetDirectory);

        // Check that the BASE_URI is correct
        Class $ProxyRoot = cl.loadClass("test.ApiSearchYahooCom_NewsSearchServiceV1");
        assertEquals(
                URI.create("http://api.search.yahoo.com/NewsSearchService/V1/"),
                $ProxyRoot.getDeclaredField("BASE_URI").get($ProxyRoot));
        
        // Check that we have the expected number of methods
        Class $NewsSearch = cl.loadClass("test.ApiSearchYahooCom_NewsSearchServiceV1$NewsSearch");
        assertNotNull($NewsSearch);

        Class $Type = cl.loadClass("test.Type");
        Class $Sort = cl.loadClass("test.Sort");
        Class $Output = cl.loadClass("test.Output");

        // Constructors
        assertNotNull($NewsSearch.getConstructor(getClientClass(), URI.class));


        // Check that we have two methods of the right name and parameters
        assertNotNull($NewsSearch.getDeclaredMethod("getAsResultSet", String.class, String.class));
        assertNotNull($NewsSearch.getDeclaredMethod("getAsXml", String.class, String.class, Class.class));
        assertNotNull($NewsSearch.getDeclaredMethod("getAsXml", String.class, String.class, getGenericTypeClass()));

        assertNotNull($NewsSearch.getDeclaredMethod("getAsResultSet", String.class, String.class,
                $Type, Integer.class, Integer.class, $Sort, String.class, $Output, String.class));
        assertNotNull($NewsSearch.getDeclaredMethod("getAsXml", String.class, String.class,
                $Type, Integer.class, Integer.class, $Sort, String.class, $Output, String.class, Class.class));
        assertNotNull($NewsSearch.getDeclaredMethod("getAsXml", String.class, String.class,
                $Type, Integer.class, Integer.class, $Sort, String.class, $Output, String.class, getGenericTypeClass()));


    }


    
    /**
     * Tests the case in which a valid wadl file exists, and we have a catalog
     * file with which to override the BASE_URI with
     */
    public void testValidWadlFilesWithCatalog() throws Exception {
        // Prepare
        Wadl2JavaMojo mojo = getMojo("valid-wadl-config-with-catalog.xml");
        File targetDirectory = (File) getVariableValueFromObject(mojo,
                "targetDirectory");
        if (targetDirectory.exists()) {
            FileUtils.deleteDirectory(targetDirectory);
        }
        setVariableValueToObject(mojo, "project", _project);

        // Record
        _project.addCompileSourceRoot(targetDirectory.getAbsolutePath());

        // Replay
        EasyMock.replay(_project);
        mojo.execute();

        // Verify
        EasyMock.verify(_project);
 
        // Create a catalog file
        
        File metainf = new File(targetDirectory, "META-INF/");
        metainf.mkdirs();
        File catalog = new File(metainf, "jax-rs-catalog.xml");
        PrintStream ps = new PrintStream(
                new FileOutputStream(catalog));
        try
        {
            ps.println("<catalog xmlns=\"urn:oasis:names:tc:entity:xmlns:xml:catalog\">");
            ps.println("  <uri name=\"http://api.search.yahoo.com/NewsSearchService/V1/\"" );
            ps.println("          uri=\"http://otherhost/\"/> ");
            ps.println("</catalog>");
        }
        finally
        {
            ps.close();
        }
        
        
        // Check that the generated code compiles
        ClassLoader cl = compile(targetDirectory);

        // Check that the BASE_URI is correct
        Class $ProxyRoot = cl.loadClass("test.ApiSearchYahooCom_NewsSearchServiceV1");
        assertEquals(
                URI.create("http://otherhost/"),
                $ProxyRoot.getDeclaredField("BASE_URI").get($ProxyRoot));
        
        
        // Check that the client also uses this URI at runtime
        //
        
        Object newsService = staticMethod("newsSearch").withParameterTypes(getClientClass())
                .in($ProxyRoot).invoke(_client);
        
        String result = method("getAsXml").withReturnType(String.class).withParameterTypes(
                String.class, String.class, Class.class).in(newsService)
                .invoke("One", "Two", String.class);
        
        assertThat(result, not(nullValue()));
        assertThat("http://otherhost/newsSearch?query=Two&appid=One",
                equalTo(_requests.get(0).getURI().toString()));
    }    
    

    
    
    /**
     * Tests the case in which a valid wadl file exists, and it it contains a method that returns
     * just text/plain
     */
    public void testNonJAXBWadlFiles() throws Exception {
        final String expectedClassName = "Localhost";
        final String pluginXmlFilename = "nonjaxb-wadl.xml";

        assertNonJAXBWadlFiles(expectedClassName, pluginXmlFilename);
    }

    /**
     * Tests the hello world file works with custom class names providing less cryptic and
     * non-changing class names for bug bug WADL-43
     * 
     * 
     * @throws Exception
     */
    public void testNonJAXBWadlFilesCustomClassName() throws Exception {
        final String expectedClassName = "MyApiClient";
        final String pluginXmlFilename = "nonjaxb-wadl-custom-names.xml";

        assertNonJAXBWadlFiles(expectedClassName, pluginXmlFilename);
    }

    /**
     * Tests the case in which a valid wadl file exists, and it it contains a method that returns
     * just text/plain
     * 
     * @param expectedClassName
     * @param pluginXmlFilename
     * @throws Exception
     * @throws IllegalAccessException
     * @throws IOException
     * @throws MojoExecutionException
     * @throws MojoFailureException
     * @throws MalformedURLException
     * @throws ClassNotFoundException
     * @throws NoSuchMethodException
     */
    private void assertNonJAXBWadlFiles(final String expectedClassName, final String pluginXmlFilename)
            throws Exception {
        // Prepare
        Wadl2JavaMojo mojo = getMojo(pluginXmlFilename);
        File targetDirectory = (File) getVariableValueFromObject(mojo, "targetDirectory");
        if (targetDirectory.exists()) {
            FileUtils.deleteDirectory(targetDirectory);
        }
        setVariableValueToObject(mojo, "project", _project);

        // Record
        _project.addCompileSourceRoot(targetDirectory.getAbsolutePath());

        // Replay
        EasyMock.replay(_project);
        mojo.execute();

        // Verify
        EasyMock.verify(_project);
        assertThat(targetDirectory, exists());
        assertThat(targetDirectory, contains("test"));
        assertThat(targetDirectory, contains("test/" + expectedClassName + ".java"));

        // Check that the generated code compiles
        ClassLoader cl = compile(targetDirectory);

        // Check that we have the expected number of methods
        Class $Helloworld = cl.loadClass("test." + expectedClassName + "$Helloworld");
        assertNotNull($Helloworld);

        // Constructors
        assertNotNull($Helloworld.getConstructor(getClientClass(), URI.class));

        // Check that we have two methods of the right name and parameters
        assertNotNull($Helloworld.getDeclaredMethod("getAsTextPlain", Class.class));
        assertNotNull($Helloworld.getDeclaredMethod("getAsTextPlain", getGenericTypeClass()));

    
        // Check that the client works at runtime
        //
        
        Class root = type("test." + expectedClassName).withClassLoader(cl).load();
        Object helloWorld = staticMethod("helloworld").withParameterTypes(getClientClass())
                .in(root).invoke(_client);
        
        String result = method("getAsTextPlain").withReturnType(String.class).withParameterTypes(
                Class.class).in(helloWorld)
                .invoke(String.class);
        
        assertThat(result, not(nullValue()));
        assertThat("GET",
                equalTo(_requests.get(0).getMethod()));
        assertThat("http://localhost:9998/helloworld",
                equalTo(_requests.get(0).getURI().toString()));
    
    }

    
    

    
    /**
     * Tests the case in which a method has multiple representations
     */
    public void testValidMutlipleContentTypes() throws Exception {
        // Prepare
        Wadl2JavaMojo mojo = getMojo("multiple-contenttypes-wadl.xml");
        File targetDirectory = (File) getVariableValueFromObject(mojo,
                "targetDirectory");
        if (targetDirectory.exists()) {
            FileUtils.deleteDirectory(targetDirectory);
        }
        setVariableValueToObject(mojo, "project", _project);

        // Record
        _project.addCompileSourceRoot(targetDirectory.getAbsolutePath());

        // Replay
        EasyMock.replay(_project);
        mojo.execute();

        // Verify
        EasyMock.verify(_project);
        assertThat(targetDirectory, exists());

        // Verify the files are in place

        // Verify
        EasyMock.verify(_project);
        assertThat(targetDirectory, exists());
        assertThat(targetDirectory, contains("test"));
        assertThat(targetDirectory, contains("test/Localhost.java"));

        // Check that the generated code compiles
        ClassLoader cl = compile(targetDirectory);



        // Check that we have the expected number of methods
        Class $Helloworld = cl.loadClass("test.Localhost$Helloworld");
        assertNotNull($Helloworld);

        // Constructors
        assertNotNull($Helloworld.getConstructor(getClientClass(), URI.class));

        // Check that we have two methods of the right name and parameters
        assertNotNull($Helloworld.getDeclaredMethod("getAsSomeClassXml"));
        assertNotNull($Helloworld.getDeclaredMethod("getAsXml", Class.class));
        assertNotNull($Helloworld.getDeclaredMethod("getAsXml", getGenericTypeClass()));

        assertNotNull($Helloworld.getDeclaredMethod("getAsSomeClassJson"));
        assertNotNull($Helloworld.getDeclaredMethod("getAsJson", Class.class));
        assertNotNull($Helloworld.getDeclaredMethod("getAsJson", getGenericTypeClass()));

        // Check that we can handle multiple content types
        
        Class root = type("test.Localhost").withClassLoader(cl).load();
        Object helloWorld = staticMethod("helloworld").withParameterTypes(getClientClass())
                .in(root).invoke(_client);
        
        // Get some XML
        
        _cannedResponse.add(new CannedResponse(
                200, "application/xml", "<someClass><integer>42</integer><string>hello</string></someClass>"));
        
        Object result = method("getAsSomeClassXml").withParameterTypes().in(helloWorld)
                .invoke();
        
        assertThat("hello", equalTo(method("getString").withReturnType(String.class).in(result).invoke()));
        assertThat(42, equalTo(method("getInteger").withReturnType(Integer.class).in(result).invoke()));

        // Get some JSON
        
        _cannedResponse.add(new CannedResponse(
                200, "application/json", "{ \"integer\" : \"42\", \"string\" : \"hello\" } "));
        
        result = method("getAsSomeClassJson").withParameterTypes().in(helloWorld)
                .invoke();
        
        assertThat("hello", equalTo(method("getString").withReturnType(String.class).in(result).invoke()));
        assertThat(42, equalTo(method("getInteger").withReturnType(Integer.class).in(result).invoke()));
    
    }
    
    

// Commented out until we work out the right thign to do with JSON schema  
    /**
     * Tests the case in which a method has multiple representations but this
     * time using json-schema rather than xml schema for the json types
     */
    public void testValidMutlipleContentJsonSchemaTypes() throws Exception {
        // Prepare
        Wadl2JavaMojo mojo = getMojo("multiple-contenttypes-jsonschema-wadl.xml");
        File targetDirectory = (File) getVariableValueFromObject(mojo,
                "targetDirectory");
        if (targetDirectory.exists()) {
            FileUtils.deleteDirectory(targetDirectory);
        }
        setVariableValueToObject(mojo, "project", _project);

        // Record
        _project.addCompileSourceRoot(targetDirectory.getAbsolutePath());

        // Replay
        EasyMock.replay(_project);
        mojo.execute();

        // Verify
        EasyMock.verify(_project);
        assertThat(targetDirectory, exists());

        // Verify the files are in place

        // Verify
        EasyMock.verify(_project);
        assertThat(targetDirectory, exists());
        assertThat(targetDirectory, contains("test"));
        assertThat(targetDirectory, contains("test/Localhost.java"));

        // Check that the generated code compiles
        ClassLoader cl = compile(targetDirectory);



        // Check that we have the expected number of methods
        Class $Root = cl.loadClass("test.Localhost$Root");
        assertNotNull($Root);

        // Constructors
        assertNotNull($Root.getConstructor(getClientClass(), URI.class));

        // We should only have six methods, removing duplicates
        assertEquals(7, $Root.getDeclaredMethods().length);
        
        //
        Class $JsonRequestMessage = cl.loadClass("test.RequestMessage");
        Class $XmlRequestMessage = cl.loadClass("message.RequestMessage");
        
        // Check that we have two methods of the right name and parameters
        assertNotNull($Root.getDeclaredMethod("putJsonAsResponseMessage", $XmlRequestMessage));
        assertNotNull($Root.getDeclaredMethod("putJsonAsResponseMessage", $JsonRequestMessage));
        assertNotNull($Root.getDeclaredMethod("putJson", Object.class, Class.class));
        assertNotNull($Root.getDeclaredMethod("putJson", Object.class, getGenericTypeClass()));

        assertNotNull($Root.getDeclaredMethod("putXmlAsResponseMessage", $XmlRequestMessage));
        assertNotNull($Root.getDeclaredMethod("putXml", Object.class, Class.class));
        assertNotNull($Root.getDeclaredMethod("putXml", Object.class, getGenericTypeClass()));

        // Check that we can handle multiple content types
        
        Class root = type("test.Localhost").withClassLoader(cl).load();
        Object rootClient = staticMethod("root").withParameterTypes(getClientClass())
                .in(root).invoke(_client);
        
        // Get some XML
        
        _cannedResponse.add(new CannedResponse(
                200, "application/xml", "<ns:responseMessage xmlns:ns=\"urn:message\"><text>hello</text></ns:responseMessage>"));
        
        Object xmlRequestMessage = constructor().in($XmlRequestMessage).newInstance();
        method("setText").withParameterTypes(String.class).in(xmlRequestMessage).invoke("Hello");
        
        Object result = method("putXmlAsResponseMessage").withParameterTypes(
                $XmlRequestMessage).in(rootClient)
                .invoke(xmlRequestMessage);
        
        assertThat("hello", equalTo(method("getText").withReturnType(String.class).in(result).invoke()));

        // Get some JSON
        
        _cannedResponse.add(new CannedResponse(
                200, "application/json", "{  \"text\" : \"hello\" } "));

        Object jsonRequestMessage = constructor().in($JsonRequestMessage).newInstance();
        method("setText").withParameterTypes(String.class).in(jsonRequestMessage).invoke("Hello");
        
        
        result = method("putJsonAsResponseMessage").withParameterTypes($JsonRequestMessage).in(rootClient)
                .invoke(jsonRequestMessage);
        
        assertThat("hello", equalTo(method("getText").withReturnType(String.class).in(result).invoke()));
    
    }
    
    
    

    /**
     * Test the case where we have the types generating as @XmlType not
     * @XmlRootElement so we need to generate some extra boilerplate code.
     */
    public void testHelloBeanInputOuputJAXB() throws Exception {
        runBeanInputOuputJAXB("hellobean-wadl.xml", "WwwExampleCom_Resource", "Bean", "bean");
    }
    
    /**
     * Test the case where we have the types generating as 
     * @XmlRootElement so we don't need to generate boilerplate code.
     */
    public void testHelloBeanInputOuputJAXBSimpleXJC() throws Exception {
        runBeanInputOuputJAXB("hellobean-wadl-simplexjc.xml", "WwwExampleCom_Resource", "Bean", "bean");
    }
    
    /**
     * Test the case where we have the root element with the same name
     * as the child element.
     */
    public void testHelloBeanSameNameWithCustomization() throws Exception {
        runBeanInputOuputJAXB("hellobean-wadl-customname.xml", "Bean", "BeanBean", "beanBean");
    }

    
    /**
     * Test the case where we have the types generating as @XmlType not
     * @XmlRootElement so we need to generate some extra boilerplate code.
     */
    private void runBeanInputOuputJAXB(
            String configurationFile,
            String className,
            String innerClassName,
            String accesorName) throws Exception {
    
        
        // Prepare
        Wadl2JavaMojo mojo = getMojo(configurationFile);
        

        
        File targetDirectory = (File) getVariableValueFromObject(mojo,
                "targetDirectory");
        if (targetDirectory.exists()) {
            FileUtils.deleteDirectory(targetDirectory);
        }
        setVariableValueToObject(mojo, "project", _project);

        // Record
        _project.addCompileSourceRoot(targetDirectory.getAbsolutePath());

        // Replay
        EasyMock.replay(_project);
        mojo.execute();

        // Verify
        EasyMock.verify(_project);
        assertThat(targetDirectory, exists());
        assertThat(targetDirectory, contains("test"));
        assertThat(targetDirectory, contains("test/" + className + ".java"));
        assertThat(targetDirectory, contains("com/example/beans/Bean.java"));

        // Check that the generated code compiles
        ClassLoader cl = compile(targetDirectory);

        // Just check that the base class works
        Class $ProxyRoot = cl.loadClass("test." + className);
        Class $ProxyInnerClass = cl.loadClass("test." + className + "$" + innerClassName);
        Class $BeanClass = cl.loadClass("com.example.beans.Bean");
        
        
        // Check we have the right mesage format
        
        assertEquals(11, $ProxyInnerClass.getDeclaredMethods().length);
        
        $ProxyInnerClass.getDeclaredMethod("getAsBean");
        $ProxyInnerClass.getDeclaredMethod("getAsXml", Class.class);
        $ProxyInnerClass.getDeclaredMethod("getAsXml", getGenericTypeClass());
        
        $ProxyInnerClass.getDeclaredMethod("putXmlAsBean",$BeanClass);
        $ProxyInnerClass.getDeclaredMethod("putXml", Object.class, Class.class);
        $ProxyInnerClass.getDeclaredMethod("putXml", Object.class, getGenericTypeClass());
        
        $ProxyInnerClass.getDeclaredMethod("postXmlAsBean",$BeanClass);
        $ProxyInnerClass.getDeclaredMethod("postXml", Object.class, Class.class);
        $ProxyInnerClass.getDeclaredMethod("postXml", Object.class, getGenericTypeClass());

        $ProxyInnerClass.getDeclaredMethod("deleteAsXml", Class.class);
        $ProxyInnerClass.getDeclaredMethod("deleteAsXml", getGenericTypeClass());
        
        // Load the source for the Proxy and check we generated the correct new JAXBElement lines
        String contents ="";
        File resource = new File(targetDirectory,"test/" + className + ".java" );
        FileInputStream fis = new FileInputStream(resource);
        try {
            byte data[] = new byte[(int)resource.length()];
            fis.read(data);
            contents = new String(data);
        }
        finally
        {
            fis.close();
        }
        
        // Make sure we wrap the @XmlType as a JAXBElement with the right
        // namespace
        
        boolean containsBinding = contents.contains("new JAXBElement(new QName(\"http://example.com/beans\", \"bean\"), com.example.beans.Bean.class, input)");
        if (configurationFile.contains("simplexjc")) {
            assertFalse(
                 containsBinding);            
        }
        else {
            assertTrue(
                 containsBinding);            
        }
        
        // Okay let try to invoke the service
        //
        
        Class root = type("test." + className).withClassLoader(cl).load();
        Object bean = staticMethod(accesorName).withParameterTypes(getClientClass()).in(root).invoke(_client);
        
        // Invoke the service
        
        
        Class beanObjClass = type("com.example.beans.Bean").withClassLoader(cl).load();
        Object beanObj = constructor().in(beanObjClass).newInstance();
        method("setMessage").withParameterTypes(String.class).in(beanObj).invoke("Bob");
        
        _cannedResponse.add(new CannedResponse(
                200, "application/xml", "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?><ns2:bean xmlns:ns2=\"http://example.com/beans\"><message>Hello Bob</message></ns2:bean>"));
        
        Object returnObj = 
                method("putXmlAsBean").withReturnType(beanObjClass)
                .withParameterTypes(beanObjClass)
                .in(bean).invoke(beanObj);
        
        assertEquals(beanObjClass,returnObj.getClass());
        assertEquals("Hello Bob",
            method("getMessage").withReturnType(String.class).in(returnObj).invoke());
    }    
    
    
    
    
    /**
     * Tests the case in which a valid wadl file exists.
     */
    public void testValidWadlFilesWithParameters() throws Exception {
        // Prepare
        Wadl2JavaMojo mojo = getMojo("parameterized-wadl.xml");
        File targetDirectory = (File) getVariableValueFromObject(mojo,
                "targetDirectory");
        if (targetDirectory.exists()) {
            FileUtils.deleteDirectory(targetDirectory);
        }
        setVariableValueToObject(mojo, "project", _project);

        // Record
        _project.addCompileSourceRoot(targetDirectory.getAbsolutePath());

        // Replay
        EasyMock.replay(_project);
        mojo.execute();

        // Verify
        EasyMock.verify(_project);
        assertThat(targetDirectory, exists());

        // Verify the files are in place

        assertThat(targetDirectory, contains("test"));
        assertThat(targetDirectory, contains("test/Localhost_JerseySchemaGenExamplesContextRootJersey.java"));
        assertThat(targetDirectory, contains("example/"));
        assertThat(targetDirectory, contains("example/IndirectReturn.java"));
        assertThat(targetDirectory, contains("example/ObjectFactory.java"));
        assertThat(targetDirectory, contains("example/SimpleParam.java"));
        assertThat(targetDirectory, contains("example/SimpleReturn.java"));



        // Check that the generated code compiles
        ClassLoader cl = compile(targetDirectory);

        // Check top level accessor
        Class $Root = cl.loadClass("test.Localhost_JerseySchemaGenExamplesContextRootJersey");
        assertNotNull($Root.getDeclaredMethod("pathParam1", getClientClass(), String.class));

        // Check that we have the expected number of methods
        Class $PathParam1 = cl.loadClass("test.Localhost_JerseySchemaGenExamplesContextRootJersey$PathParam1");
        assertNotNull($PathParam1);

        // Constructors
        assertNotNull($PathParam1.getConstructor(getClientClass(), URI.class, String.class));

        // Check that we have two methods of the right name and parameters
        assertNotNull($PathParam1.getDeclaredMethod("getParam1"));
        assertNotNull($PathParam1.getDeclaredMethod("setParam1", String.class));

        // Check accessors
        assertNotNull($PathParam1.getDeclaredMethod("param2", String.class));


        // Go on level down
        Class $PathParam2 = $PathParam1.getDeclaredClasses()[0];
        assertThat($PathParam2.getSimpleName(), equalTo("Param2"));
        assertNotNull($PathParam2);

        // Constructors
        assertNotNull($PathParam2.getConstructor(getClientClass(), URI.class, String.class));

        // Check that we have two methods of the right name and parameters
//        assertNotNull($PathParam2.getDeclaredMethod("getParam1"));
//        assertNotNull($PathParam2.getDeclaredMethod("setParam1", String.class));
        assertNotNull($PathParam2.getDeclaredMethod("getParam2"));
        assertNotNull($PathParam2.getDeclaredMethod("setParam2", String.class));


        // Check that we have two methods of the right name and parameters
        assertNotNull($PathParam2.getDeclaredMethod("getAsSimpleReturn"));
        assertNotNull($PathParam2.getDeclaredMethod("getAsXml", Class.class));
        assertNotNull($PathParam2.getDeclaredMethod("getAsXml", getGenericTypeClass()));
        
        
        // Check that the service is invoked
        
        Class root = type("test.Localhost_JerseySchemaGenExamplesContextRootJersey").withClassLoader(cl)
                .load();

        // Set first parameter and then change it
        //
        Object pathParam1 = staticMethod("pathParam1").withParameterTypes(getClientClass(), String.class)
                .in(root).invoke(_client, "OriginalParam1");
        
        Object pathParma1in2 = method("setParam1").withParameterTypes(String.class).in(pathParam1).invoke("ReplacementParam1");
        
        assertThat("Should be different instances",pathParam1, not(equalTo(pathParma1in2)));
        
        // Now set the second parameter
        //
        
        Object param2 = method("param2").withParameterTypes(String.class).in(pathParma1in2).invoke("Param2");
        
        assertThat("Param2", equalTo(method("getParam2").withReturnType(String.class).in(param2).invoke()));
        
        // Now send the request, we really don't care abou the response
        //
        
        String simpleReturn = method("getAsXml").withReturnType(String.class).withParameterTypes(Class.class).in(param2)
                .invoke(String.class);
        
        // Check that the the URL is correct
        
        assertThat("http://localhost:7101/JerseySchemaGen-Examples-context-root/jersey/path/ReplacementParam1/Param2",
                equalTo(_requests.get(0).getURI().toString()));
    }


    /**
     * Tests the case where we have a response but no content type, previously
     * this would have generated a void return type; but due to a bug in the
     * code the return type was being used as the class type, this was not
     * being detected by the other relevant unit tests because the methods are
     * not symetrical.
     */
    public void testNoResponseContent() throws Exception {
        // Prepare
        Wadl2JavaMojo mojo = getMojo("no-response-content.xml");
        File targetDirectory = (File) getVariableValueFromObject(mojo,
                "targetDirectory");
        if (targetDirectory.exists()) {
            FileUtils.deleteDirectory(targetDirectory);
        }
        setVariableValueToObject(mojo, "project", _project);

        // Record
        _project.addCompileSourceRoot(targetDirectory.getAbsolutePath());

        // Replay
        EasyMock.replay(_project);
        mojo.execute();

        // Verify
        EasyMock.verify(_project);
        assertThat(targetDirectory, exists());

        // Verify the files are in place

        assertThat(targetDirectory, contains("test"));
        assertThat(targetDirectory, contains("test/Localhost_JerseySchemaGenExamplesContextRootJersey.java"));
        assertThat(targetDirectory, contains("example/"));
        assertThat(targetDirectory, contains("example/ObjectFactory.java"));
        assertThat(targetDirectory, contains("example/SimpleInput.java"));



        // Check that the generated code compiles
        ClassLoader cl = compile(targetDirectory);

        // Check top level accessor
        Class $Root = cl.loadClass("test.Localhost_JerseySchemaGenExamplesContextRootJersey");

        // Check that we have the expected number of methods
        Class $PathParam1 = cl.loadClass("test.Localhost_JerseySchemaGenExamplesContextRootJersey$Path");
        assertNotNull($PathParam1);

        // Constructors
        assertNotNull($PathParam1.getConstructor(getClientClass(), URI.class));

        // Check that we have two methods of the right name and parameters
        
        Class $simpleInput = cl.loadClass("example.SimpleInput");

        Method $putResponse = $PathParam1.getDeclaredMethod("putXml", $simpleInput);
        assertNotNull($putResponse);
        assertEquals(getResponseClass(), $putResponse.getReturnType());
        
        // Check that the contents of the java file contains the correct
        // JAXBElement, and doesn't incorrectly use the return type
        String t = "new JAXBElement.new QName.\"urn:example\", \"simpleInput\"., SimpleInput.class, input..";
        
        
        File proxyFile = new File(targetDirectory, "test/Localhost_JerseySchemaGenExamplesContextRootJersey.java");
        DataInputStream input = new DataInputStream(new FileInputStream(proxyFile));
        byte data[] = new byte[(int) proxyFile.length()];
        input.readFully(data);
        String contents = new String(data);
        Matcher matcher = Pattern.compile(t).matcher(contents);
        assertTrue(matcher.find());
        assertFalse(matcher.find());
        
        // Invoke the service and see that the object is consumed properly
        //
        
        Class root = type("test.Localhost_JerseySchemaGenExamplesContextRootJersey")
                .withClassLoader(cl).load();
        Object path = staticMethod("path").withParameterTypes(getClientClass())
                .in(root).invoke(_client);
        
        Class simpleType = type("example.SimpleInput").withClassLoader(cl).load();
        Object simpleObj = constructor().in(simpleType).newInstance();
        
        Object cr = method("putXml").withReturnType(getResponseClass()).withParameterTypes(simpleType)
                .in(path).invoke(simpleObj);
                
        // This will most likley be true if we reach this line, otherwise the
        // original message will have been lost
        assertThat(200, equalTo(
                method("getStatus").withReturnType(int.class).in(cr).invoke()));
    }

    
    
    
    /**
     * Verify as per WADL-37 that query and header parameters are not inhereted
     * from parent resources
     */
    public void testValidWadlFilesWithNestedParameters() throws Exception {
        // Prepare
        Wadl2JavaMojo mojo = getMojo("nested-wadl.xml");
        File targetDirectory = (File) getVariableValueFromObject(mojo,
                "targetDirectory");
        if (targetDirectory.exists()) {
            FileUtils.deleteDirectory(targetDirectory);
        }
        setVariableValueToObject(mojo, "project", _project);

        // Record
        _project.addCompileSourceRoot(targetDirectory.getAbsolutePath());

        // Replay
        EasyMock.replay(_project);
        mojo.execute();

        // Verify
        EasyMock.verify(_project);
        assertThat(targetDirectory, exists());

        // Verify the files are in place

        assertThat(targetDirectory, contains("test"));
        assertThat(targetDirectory, contains("test/Nested.java"));
 
        // Check that the generated code compiles
        ClassLoader cl = compile(targetDirectory);

//        // Check top level accessor
//        Class $Root = cl.loadClass("test.Nested$Root");
//
//        
//        // Check we have the matrix accessor on the sub matrix value
//        assertNotNull($Root.getDeclaredMethod("setRootm", String.class));
//        assertNotNull($Root.getDeclaredMethod("getRootm"));
//        assertNotNull($Root.getDeclaredMethod("setRepeatingm", List.class));
//        assertNotNull($Root.getDeclaredMethod("getRepeatingm"));
//        
//        
//        // Check that we have the expected number of methods
//        Class $Sub = cl.loadClass("test.Nested$Root$Sub");
//        assertNotNull($Sub);
//
//        // Check we have the matrix accessor on the sub matrix value
//        assertNotNull($Sub.getDeclaredMethod("setSubm", String.class));
//        assertNotNull($Sub.getDeclaredMethod("getSubm"));
//
//        
//        // Check that we have two methods of the right name and parameters
//        // Should only have four string parametes as per WADL-32 the
//        // root resource parametes are not inhereted
//        assertNotNull($Sub.getDeclaredMethod("getAs", String.class, String.class, List.class,String.class,  String.class, List.class, Class.class));
//        assertNotNull($Sub.getDeclaredMethod("getAs", String.class, String.class, List.class, String.class,  String.class, List.class, GenericType.class));

        // Right lets to to invoke the servuce using our fake client
        //
        
        Class client = type("test.Nested").withClassLoader(cl).load();
        Object root1 = staticMethod("root")
                .withParameterTypes(getClientClass(), URI.class).in(client).invoke(
                    _client, URI.create("http://example.com/"));
        
        // Set the rootm value and call
        Object root2 = method("setRootm").withParameterTypes(String.class).in(root1).invoke("XXRootM");
        assertEquals("XXRootM", method("getRootm").withReturnType(String.class).in(root2).invoke());
        
        // Set the repeatingm value
        List<String> repatingMatrixValues = Arrays.asList("XXOne", "XXTwo");
        Object root3 = method("setRepeatingm").withParameterTypes(List.class).in(root2).invoke(repatingMatrixValues);
        assertEquals(repatingMatrixValues, method("getRepeatingm").withReturnType(List.class).in(root3).invoke());
        
        // Get hold of the sub
        
        Object sub = method("sub").in(root3).invoke();
        
        // Check we have modified the matrix property

        sub = method("setSubm").withParameterTypes(String.class).in(sub).invoke("XXSubM");
        assertEquals("XXSubM", method("getSubm").withReturnType(String.class).in(sub).invoke());
    
        // Now lets try to invoke the service
        
        List<String> repatingQueryValues = Arrays.asList("XXOne", "XXTwo");
        List<String> repatingHeaderValues = Arrays.asList("XXOne", "XXTwo");
        
        // Populate a canned response
        
        _cannedResponse.add(new CannedResponse(
                200, "text/plain", "Nested"));
        
        //
        
        String result = method("getAs").withReturnType(String.class)
                .withParameterTypes(String.class, String.class, List.class,String.class,  String.class, List.class, Class.class)
                .in(sub)
                .invoke(
                    "subq", "submethodq", repatingQueryValues,
                    "subh", "submethodh", repatingHeaderValues,
                    String.class);
        // Check expected response seen
        assertEquals("Check that our precanned response is used","Nested", result);
        // Check URI
        String uri = "http://example.com/root;rootM=XXRootM;repeatingM=XXOne;repeatingM=XXTwo/sub;subM=XXSubM;subM=XXSubM?subQ=subq&subMethodQ=submethodq&repeatingQ=XXOne&repeatingQ=XXTwo";
        assertEquals("Should only have recorded one request",1, _requests.size());
        String actualURI = _requests.get(0).getURI().toString();
        
        // Removed until Jersey-1369 is resolved
        //assertEquals("We should have a really funky URI",uri, actualURI);
        
    }

    
    /**
     * Tests the case where the methods produce application/xml but don't
     * have a matching schema. This results in duplicate methods being produced.
     * 
     * See WADL issue 41
     */
    public void testJAXBMissingSchema() throws Exception {
        // Prepare
        Wadl2JavaMojo mojo = getMojo("jaxb-missing-schema.xml");
        File targetDirectory = (File) getVariableValueFromObject(mojo,
                "targetDirectory");
        if (targetDirectory.exists()) {
            FileUtils.deleteDirectory(targetDirectory);
        }
        setVariableValueToObject(mojo, "project", _project);

        // Record
        _project.addCompileSourceRoot(targetDirectory.getAbsolutePath());

        // Replay
        EasyMock.replay(_project);
        mojo.execute();

        // Verify
        EasyMock.verify(_project);
        assertThat(targetDirectory, exists());
        assertThat(targetDirectory, contains("test"));
        assertThat(targetDirectory, contains("test/Localhost_Project1Jersey.java"));

        // Check that the generated code compiles
        ClassLoader cl = compile(targetDirectory);



        // Check that we have the expected number of methods
        Class $Helloworld = cl.loadClass("test.Localhost_Project1Jersey$Put");
        assertNotNull($Helloworld);

        // Constructors
        assertNotNull($Helloworld.getConstructor(getClientClass(), URI.class));

        // Check that we have two methods of the right name and parameters
        assertNotNull($Helloworld.getDeclaredMethod("getAsXml", Class.class));
        assertNotNull($Helloworld.getDeclaredMethod("getAsXml", getGenericTypeClass()));

        assertNotNull($Helloworld.getDeclaredMethod("putXml", Object.class, Class.class));
        assertNotNull($Helloworld.getDeclaredMethod("putXml", Object.class, getGenericTypeClass()));

        // Verify that in both cases the method is actually invoked, in
        // liu of functional tests for the moment
        File proxyFile = new File(targetDirectory, "test/Localhost_Project1Jersey.java");
        DataInputStream input = new DataInputStream(new FileInputStream(proxyFile));
        byte data[] = new byte[(int) proxyFile.length()];
        input.readFully(data);
        String contents = new String(data);
        Matcher matcher = Pattern.compile(getReturnStatmentRegex()).matcher(contents);
        // Make sure we only have four return methods of this kind
        matcher.find();
        matcher.find();
        matcher.find();
        assertTrue(matcher.find());
        assertFalse(matcher.find());
        
        
        // TODO put in functional test
        // and push up so that this test can be shared
        //
    }


 

    /**
     * The version of the Yahoo WADL was failing with a duplicate method
     * problem on compilation. This was reported as oracle bug 14534583
     * the problem was that the version of the Yahoo WADL from SoapUI website
     * was using a fault element in the response and this was not being upgraded 
     * properly to 2009 from 2006 and the status code was lost and it was not 
     * transformed to a separate response element. This was causing compilation to
     * fail as the fault condition was being mapped as another representation.
     */
    public void testSoapYahooWadl() throws Exception {
        // Prepare
        Wadl2JavaMojo mojo = getMojo("soapui-yahoo-wadl-config.xml");
        File targetDirectory = (File) getVariableValueFromObject(mojo,
                "targetDirectory");
        if (targetDirectory.exists()) {
            FileUtils.deleteDirectory(targetDirectory);
        }
        setVariableValueToObject(mojo, "project", _project);

        // Record
        _project.addCompileSourceRoot(targetDirectory.getAbsolutePath());

        // Replay
        EasyMock.replay(_project);
        mojo.execute();

        // Verify
        EasyMock.verify(_project);
        assertThat(targetDirectory, exists());
        assertThat(targetDirectory, contains("test"));
        assertThat(targetDirectory, contains("test/ApiSearchYahooCom_NewsSearchServiceV1.java"));
        assertThat(targetDirectory, contains("test/Output.java"));
        assertThat(targetDirectory, contains("test/Type.java"));
        assertThat(targetDirectory, contains("test/Sort.java"));
        assertThat(targetDirectory, contains("test/SearchErrorException.java"));
        assertThat(targetDirectory, contains("yahoo/api/ObjectFactory.java"));
        assertThat(targetDirectory, contains("yahoo/api/Error.java"));
        assertThat(targetDirectory, contains("yahoo/yn/ImageType.java"));
        assertThat(targetDirectory, contains("yahoo/yn/ObjectFactory.java"));
        assertThat(targetDirectory, contains("yahoo/yn/ResultSet.java"));
        assertThat(targetDirectory, contains("yahoo/yn/ResultType.java"));

        // Check that the generated code compiles
        ClassLoader cl = compile(targetDirectory);
    }
    


    /**
     * Add a test to verify that exceptions are throw properly
     */
    public void testSimpleExceptionCase() throws Exception {
        // Prepare
        Wadl2JavaMojo mojo = getMojo("soapui-yahoo-wadl-config.xml");
        File targetDirectory = (File) getVariableValueFromObject(mojo,
                "targetDirectory");
        if (targetDirectory.exists()) {
            FileUtils.deleteDirectory(targetDirectory);
        }
        setVariableValueToObject(mojo, "project", _project);

        // Record
        _project.addCompileSourceRoot(targetDirectory.getAbsolutePath());

        // Replay
        EasyMock.replay(_project);
        mojo.execute();

        // Verify
        EasyMock.verify(_project);

        // Check that the generated code compiles
        ClassLoader cl = compile(targetDirectory);
        
        // Invoke the service to provoke and error
        //
        
        Class client = type("test.ApiSearchYahooCom_NewsSearchServiceV1").withClassLoader(cl).load();
        Object newsService = staticMethod("newsSearch")
                .withParameterTypes(getClientClass(), URI.class).in(client).invoke(
                    _client, URI.create("http://example.com/"));
        
        // Test out that the right exception is thrown
        //
        
        
        _cannedResponse.add(new CannedResponse(
                400, "text/xml", "<Error xmlns=\"urn:yahoo:api\"><Message>42</Message></Error>"));
        
        try
        {
            String result = method("getAsTextXml").withReturnType(String.class)
                    .withParameterTypes(String.class, String.class, Class.class)
                    .in(newsService).invoke("not","at", String.class);
            
            assertThat("Should have thrown an exceptio and never reached this line", false, equalTo(true));
        }
        catch (WebApplicationException ex)
        {
            Object faultInfo = method("getFaultInfo").in(ex).invoke();
            // Check the content if in the right place
            List content = field("content").ofType(List.class).in(faultInfo).get();
            assertThat(1, equalTo(content.size()));
            //
            String message = ((Element)((JAXBElement)content.get(0)).getValue()).getTextContent();
            assertThat("42", equalTo(message));
        }
    }
    
    
    
    /**
     * In this case there is a resource path of / which would result in
     * an empty string after converting to a classname, instead this now
     * get converted to "Root".
     */
    public void testValidWadlOfficeDirectory() throws Exception {
        // Prepare
        Wadl2JavaMojo mojo = getMojo("officedirectory-wadl.xml");
        File targetDirectory = (File) getVariableValueFromObject(mojo,
                "targetDirectory");
        if (targetDirectory.exists()) {
            FileUtils.deleteDirectory(targetDirectory);
        }
        setVariableValueToObject(mojo, "project", _project);

        // Record
        _project.addCompileSourceRoot(targetDirectory.getAbsolutePath());

        // Replay
        EasyMock.replay(_project);
        mojo.execute();

        // Verify
        EasyMock.verify(_project);
        assertThat(targetDirectory, exists());

        // Check that the generated code compiles
        ClassLoader cl = compile(targetDirectory);

        // Verify the files are in place

        assertThat(targetDirectory, contains("test"));
        assertThat(targetDirectory, contains("test/Localhost_OfficeDirectory.java"));
        
        // Verify that the / path has been mapped to the name root
        
        Class $Root = cl.loadClass("test.Localhost_OfficeDirectory$Root");
        
    }    
}