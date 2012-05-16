package org.sugarj;

import static org.sugarj.common.ATermCommands.getApplicationSubterm;
import static org.sugarj.common.ATermCommands.getList;
import static org.sugarj.common.ATermCommands.isApplication;
import static org.sugarj.common.Environment.sep;
import static org.sugarj.common.Log.log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.Serializable;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.eclipse.core.runtime.FileLocator;
import org.spoofax.interpreter.terms.IStrategoTerm;
import org.spoofax.terms.Term;
import org.strategoxt.HybridInterpreter;
import org.strategoxt.java_front.pp_java_string_0_0;
import org.strategoxt.lang.Context;
import org.sugarj.common.ATermCommands;
import org.sugarj.common.Environment;
import org.sugarj.common.IErrorLogger;
import org.sugarj.common.FileCommands;
import org.sugarj.common.JavaCommands;
import org.sugarj.common.path.Path;
import org.sugarj.common.path.RelativePath;
import org.sugarj.common.path.RelativeSourceLocationPath;
import org.sugarj.driver.sourcefilecontent.JavaSourceFileContent;

public class JavaLib extends LanguageLib implements Serializable {

	private static final long serialVersionUID = 1817193221140795776L;
	
	private transient File libDir;
	private transient File libTmpDir;

	private Set<RelativePath> generatedJavaClasses = new HashSet<RelativePath>();

	private Path javaOutFile;

	private JavaSourceFileContent javaSource;

	private String relPackageName;
	
	
	@Override
	public List<File> getGrammars() {
		List<File> grammars = new LinkedList<File>(super.getGrammars());
		grammars.add(ensureFile("org/sugarj/languages/SugarJ.def"));
		grammars.add(ensureFile("org/sugarj/languages/Java-15.def"));
		return Collections.unmodifiableList(grammars);
	}
	
	@Override
	public File getInitGrammar() {
		return ensureFile("org/sugarj/java/init/initGrammar.sdf");
		//return ensureFile("org/sugarj/languages/SugarJ.def");
	}

	@Override
	public String getInitGrammarModule() {
		return "org/sugarj/java/init/initGrammar";
		//return "org/sugarj/languages/sugarJ";
	}

	@Override
	public File getInitTrans() {
		return ensureFile("org/sugarj/java/init/InitTrans.str");
	}
	
	@Override
	public String getInitTransModule() {
		return "org/sugarj/java/init/InitTrans";
	}

	@Override
	public File getInitEditor() {
		return ensureFile("org/sugarj/java/init/initEditor.serv");
	}

	@Override
	public String getInitEditorModule() {
		return "org/sugarj/java/init/initEditor";
	}
	
	@Override
	public File getLibraryDirectory() {
		if (libDir == null) {	// set up directories first
			String thisClassPath = "org/sugarj/JavaLib.class";
			URL thisClassURL = JavaLib.class.getClassLoader().getResource(thisClassPath);
			
			System.out.println(thisClassURL);
			
			if (thisClassURL.getProtocol().equals("bundleresource"))
			  try {
			    thisClassURL = FileLocator.resolve(thisClassURL);
			  } catch (IOException e) {
			    e.printStackTrace();
			  }
			
			String classPath = thisClassURL.getPath();
			String binPath = classPath.substring(0, classPath.length() - thisClassPath.length());
			
			libDir = new File(binPath);
		}
		
		return libDir;
	}
	
	private File getTmpLibraryDirectory() {
		if (libTmpDir == null)
			try {
				File f = File.createTempFile("org.sugarj.javalib", "");
				f.delete();
				f.mkdir();
				libTmpDir = f;
			} catch (IOException e) {
				e.printStackTrace();
			}
		
		return libTmpDir;
	}
	
	public File ensureFile(String resource) {
		File f = new File(getLibraryDirectory().getPath() + File.separator + resource);
		
		System.out.println("javalib ensure file: " + f);
		
		if (f.exists())
			return f;

		f = new File(getTmpLibraryDirectory().getPath() + "/" + resource);
		System.out.println("f does not exist, making temp file " + f);
		f.getParentFile().mkdirs();

		try {
			InputStream in = LanguageLib.class.getClassLoader().getResourceAsStream(resource);
			if (in == null)
				return  new File(getLibraryDirectory().getPath() + File.separator + resource);

			FileOutputStream fos = new FileOutputStream(f);
			byte[] bs = new byte[256];
			while (in.read(bs) >= 0)
				fos.write(bs);
			fos.close();
			in.close();
		} catch (IOException e) {
			e.printStackTrace();
		}

		return f;
	}

	
	  public static void main(String args[]) throws URISyntaxException {
		JavaLib jl = new JavaLib();
		
		for (File file : jl.getGrammars()) 
			exists(file);


	    exists(jl.getInitGrammar());
	    exists(jl.getInitTrans());
	    exists(jl.getInitEditor());
	    exists(jl.libDir);
	  }
	  
	  private static void exists(File file) {
	    if (file.exists())
	      System.out.println(file.getPath() + " exists.");
	    else
	      System.err.println(file.getPath() + " does not exist.");
	  }

/*	@Override
	public ICompilerCommands getCompilerCommands() {
		// singleton pattern. 
		// XXX: Also integrate compiler commands into language library or keep it separate to support pluggable compilers more easily?
		if (javaCommands == null)
			javaCommands = new JavaCommands();

		return javaCommands;
	}
*/

	@Override
	public String getGeneratedFileExtension() {
		return ".class";
	}

	@Override
	public String getSugarFileExtension() {
		return ".sugj";
	}
	
	// --------------------
	// stuff from javaDriver here

	public void init() {
	    javaOutFile = null;
	    javaSource = null;   
	  }

	
	private void checkPackageName(IStrategoTerm toplevelDecl, RelativeSourceLocationPath sourceFile, IErrorLogger errorLog) {
	    if (sourceFile != null) {
	      String packageName = relPackageName == null ? "" : relPackageName.replace('/', '.');
	      
	      String rel = FileCommands.dropExtension(sourceFile.getRelativePath());
	      int i = rel.lastIndexOf('/');
	      String expectedPackage = i >= 0 ? rel.substring(0, i) : rel;
	      expectedPackage = expectedPackage.replace('/', '.');
	      if (!packageName.equals(expectedPackage))
	        setErrorMessage(
	            toplevelDecl,
	            "The declared package '" + packageName + "'" +
	            " does not match the expected package '" + expectedPackage + "'.", errorLog);
	    }
	  }

//	public void checkSourceOutFile(Environment environment, RelativeSourceLocationPath sourceFile) {
//	    if (javaOutFile == null)
//	      setJavaOutFile(environment.createBinPath(getRelativeNamespace() + FileCommands.fileName(sourceFile) + ".java"));
//	  }

	// XXX: move this to language driver?
	  // XXX: Think of a good name -- what does this actually do?
	  // from ModuleSystemCommands
	  public String extractImportedModuleName(IStrategoTerm toplevelDecl, HybridInterpreter interp) throws IOException {
	    String name = null;
	    log.beginTask("Extracting", "Extract name of imported module");
	    try {
	      if (isApplication(toplevelDecl, "TypeImportDec"))
	        name = prettyPrint(toplevelDecl.getSubterm(0), interp);
	      
	      if (isApplication(toplevelDecl, "TypeImportOnDemandDec"))
	        name = prettyPrint(toplevelDecl.getSubterm(0), interp) + ".*";
	    } finally {
	      log.endTask(name);
	    }
	    return name;
	  }

	// was: getGeneratedJavaClasses
	  // XXX: think of a better name (classes -> binary files? compiled files?)
	  public Set<RelativePath> getGeneratedFiles() {
	    return generatedJavaClasses;
	  }

	// was: getRelPackageName
	  public String getNamespace() {
	    return relPackageName;
	  }

	  public String extractNamespaceName(IStrategoTerm toplevelDecl, HybridInterpreter interp) throws IOException {
	      String packageName = prettyPrint(getApplicationSubterm(toplevelDecl, "PackageDec", 1), interp);

	      return packageName;
	  }
	  
	public Path getOutFile() {
	    return javaOutFile;
	  }

	// was: getRelPackageNameSep
	  // XXX: Think of a better name
	  public String getRelativeNamespace() {
	    if (relPackageName == null || relPackageName.isEmpty())
	      return "";
	    
	    return relPackageName + sep;
	  }

	public JavaSourceFileContent getSource() {
	    return javaSource;
	  }

	public boolean isEditorServiceDec(IStrategoTerm decl) {
	    return isApplication(decl, "EditorServicesDec");
	  }

	public boolean isImportDec(IStrategoTerm decl) {
	    return isApplication(decl, "TypeImportDec") || isApplication(decl, "TypeImportOnDemandDec");
	  }

	// ----------------
	  public boolean isLanguageSpecificDec(IStrategoTerm decl) {
	    return  isApplication(decl, "ClassDec") ||
	            isApplication(decl, "InterfaceDec") ||
	            isApplication(decl, "EnumDec") ||
	            isApplication(decl, "AnnoDec");
	  }

	public boolean isPlainDec(IStrategoTerm decl) {
	    return isApplication(decl, "PlainDec");         // XXX: Decide what to do with "Plain"--leave in the language or create a new "Plain" language
	  }

	public boolean isSugarDec(IStrategoTerm decl) {
	    return isApplication(decl, "SugarDec");
	  }

	public boolean isNamespaceDec(IStrategoTerm decl) {
		return isApplication(decl, "PackageDec");
	}
	
	/**
	   * Pretty prints the content of a Java AST in some file.
	   * 
	   * @param aterm the name of a file which contains an aterm which encodes a Java AST
	   * @throws IOException 
	   */
	  // XXX: This should be abstracted and moved to the language implementation
	  // Where to get pp_java_string_0_0 ? What should be changed to allow other languages' pretty printers?
	  // What to do with Term.asJavaString ?
	  public String prettyPrint(IStrategoTerm term, HybridInterpreter interp) throws IOException {
		System.err.println("---\n prettyprint context:");
		Context ctx = interp.getCompiledContext();
		System.err.println(ctx);
		System.err.println("prettyprint term:");
		System.err.println(term);
		//IStrategoTerm string = pp_java_string_0_0.instance.invoke(interp.getCompiledContext(), term);
		IStrategoTerm string = pp_java_string_0_0.instance.invoke(ctx, term);
		System.err.println("prettyprint string:");
		System.err.println(string);
	    if (string != null)
	      return Term.asJavaString(string);
	    
	    throw new RuntimeException("pretty printing java AST failed: " + term);
	  }

	@Override
	  public void processLanguageSpecific(IStrategoTerm toplevelDecl, Environment environment, HybridInterpreter interp) throws IOException {
	    IStrategoTerm dec =  isApplication(toplevelDecl, "JavaTypeDec") ? getApplicationSubterm(toplevelDecl, "JavaTypeDec", 0) : toplevelDecl;   // XXX: Extract JavaTypeDec stuff
	    
	    String decName = Term.asJavaString(dec.getSubterm(0).getSubterm(1).getSubterm(0));
	    
	    RelativePath clazz = environment.createBinPath(getRelativeNamespace() + decName + ".class");
	    
	    generatedJavaClasses.add(clazz);
	    javaSource.addBodyDecl(prettyPrint(dec, interp));
	  }

	// was: processPackageDec
	  public void processNamespaceDec(IStrategoTerm toplevelDecl, Environment environment, HybridInterpreter interp, IErrorLogger errorLog, RelativeSourceLocationPath sourceFile, RelativeSourceLocationPath sourceFileFromResult) throws IOException {
	    String packageName = extractNamespaceName(toplevelDecl, interp);
		  
		relPackageName = getRelativeModulePath(packageName);
	
	    log.log("The SDF / Stratego package name is '" + relPackageName + "'.");
	
	    checkPackageName(toplevelDecl, sourceFile, errorLog);
	
	    if (javaOutFile == null)
	      javaOutFile = environment.createBinPath(getRelativeNamespace() + FileCommands.fileName(sourceFileFromResult) + ".java");			// XXX: Can we just reuse sourceFile here?
	
	    // moved here before depOutFile==null check
	    javaSource.setNamespaceDecl(prettyPrint(toplevelDecl, interp));
	    checkPackageName(toplevelDecl, sourceFileFromResult, errorLog);
	  }

	private void setErrorMessage(IStrategoTerm toplevelDecl, String msg, IErrorLogger errorLog) {
	    // XXX: Merge with setErrorMessage from Driver
	    errorLog.logError(msg);
	    ATermCommands.setErrorMessage(toplevelDecl, msg);
	  }

	public void setJavaOutFile(Path javaOutFile) {
	    this.javaOutFile = javaOutFile;
	  }

	public void setupSourceFile(RelativePath sourceFile, Environment environment) {
	    javaOutFile = environment.createBinPath(FileCommands.dropExtension(sourceFile.getRelativePath()) + ".java");
	    javaSource = new JavaSourceFileContent();
	    javaSource.setOptionalImport(false);
	  }

	@Override
	public LanguageLibFactory getFactoryForLanguage() {
		return new JavaLibFactory();
	}
	
	// from Result
	public void compile(List<Path> javaOutFiles, Path bin, List<Path> path, Set<? extends Path> generatedJavaClasses, Map<Path, Integer> generatedFileHashes, HybridInterpreter interp, boolean generateFiles) throws IOException {
		if (generateFiles) {
			JavaCommands.javac(javaOutFiles, bin, path);
			for (Path cl : generatedJavaClasses)
				generatedFileHashes.put(cl, FileCommands.fileHash(cl));
		}
	}

	@Override
	public String getImportedModulePath(IStrategoTerm toplevelDecl, HybridInterpreter interp) throws IOException {
	      String importModule = extractImportedModuleName(toplevelDecl, interp);
	      String modulePath = getRelativeModulePath(importModule);
	      
	      return modulePath;
	}
	
	  private String getRelativeModulePath(String module) {
		    return module.replace(".", Environment.sep);
		  }

	  
	@Override
	public void addImportModule(IStrategoTerm toplevelDecl,
			HybridInterpreter interp) throws IOException {
		javaSource.addImport(extractImportedModuleName(toplevelDecl, interp).replace('/', '.'));
	}

	@Override
	public void addCheckedImportModule(IStrategoTerm toplevelDecl,
			HybridInterpreter interp) throws IOException {
		javaSource.addCheckedImport(getImportedModulePath(toplevelDecl,  interp).replace('/', '.'));
	}

	@Override
	public String getSugarName(IStrategoTerm decl, HybridInterpreter interp) throws IOException {
		IStrategoTerm head = getApplicationSubterm(decl, "SugarDec", 0);
        String extName =
                prettyPrint(
                getApplicationSubterm(head, "SugarDecHead", 1), interp);    

        return extName;
	}

	@Override
	public int getSugarAccessibility(IStrategoTerm decl) {
		IStrategoTerm head = getApplicationSubterm(decl, "SugarDec", 0);
		IStrategoTerm mods = getApplicationSubterm(head, "SugarDecHead", 0);
		
        for (IStrategoTerm t : getList(mods))
        	if (isApplication(t, "Public")) 
        		return LanguageLib.PUBLIC_SUGAR;
        
        return LanguageLib.PRIVATE_SUGAR;
	}

	@Override
	public IStrategoTerm getSugarBody(IStrategoTerm decl) {
		IStrategoTerm body= getApplicationSubterm(decl, "SugarDec", 1);
		IStrategoTerm sugarBody = getApplicationSubterm(body, "SugarBody", 0);
		
		return sugarBody;
	}


}
