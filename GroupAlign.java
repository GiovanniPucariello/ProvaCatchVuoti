/*
 * $Id: GroupAlign.java 1820 2013-03-06 10:13:00Z euzenat $
 *
 * Copyright (C) 2003 The University of Manchester
 * Copyright (C) 2003 The University of Karlsruhe
 * Copyright (C) 2003-2008, 2010-2013 INRIA
 * Copyright (C) 2004, Université de Montréal
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public License
 * as published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 * 
 * You should have received a copy of the GNU Lesser General Public
 * License along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA 02111-1307
 * USA.
 */

/* This program evaluates the results of several ontology aligners in a row.
*/
package fr.inrialpes.exmo.align.cli;

import com.sun.org.apache.xalan.internal.xsltc.cmdline.getopt.GetOpt;
import org.semanticweb.owl.align.Alignment;
import org.semanticweb.owl.align.AlignmentException;
import org.semanticweb.owl.align.AlignmentProcess;
import org.semanticweb.owl.align.AlignmentVisitor;

import fr.inrialpes.exmo.align.impl.Annotations;
import fr.inrialpes.exmo.align.impl.Namespace;
import fr.inrialpes.exmo.align.parser.AlignmentParser;

import fr.inrialpes.exmo.ontowrap.OntologyFactory;

import java.io.*;
import java.net.URI;
import java.lang.Integer;
import java.lang.Long;
import java.net.URISyntaxException;
import java.util.Properties;

import gnu.getopt.LongOpt;
import gnu.getopt.Getopt;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** A batch class for an OWL ontology alignment processing.
    It aligns all the ontology pairs denoted 
    under subdirectory of the current directory.
    
    <pre>
    java -cp procalign.jar fr.inrialpes.exmo.align.util.GroupAlign [options]
    </pre>

    where the options are:
    <pre>
        --alignment=filename -a filename Start from an XML alignment file
	--params=filename -p filename   Read the parameters in file
	--name=filename -n filename output results in filename.rdf
	--impl=className -i classname           Use the given alignment implementation.
	--renderer=className -r className       Specifies the alignment renderer
	--debug=level -d level
   </pre>

<pre>
$Id: GroupAlign.java 1820 2013-03-06 10:13:00Z euzenat $
</pre>

@author Sean K. Bechhofer
@author Jérôme Euzenat
    */

public class GroupAlign {
final static Logger logger = LoggerFactory.getLogger(GroupAlign.class);
    Properties params = null;
    String filename = "align";
    String paramfile = null;
    String urlprefix = null;
    String source = "onto1.rdf";
    String target = "onto.rdf";
    URI uri1 = null;
    String initName = null;
    int debug = 0;
    String alignmentClassName = "fr.inrialpes.exmo.align.impl.method.StringDistAlignment";
    String rendererClass = "fr.inrialpes.exmo.align.impl.renderer.RDFRendererVisitor";
    String ontoDir = null;

    public static void testGroupAlign(String[] args) {
	try { new GroupAlign().run( args ); }
	catch (Exception ex) { ex.printStackTrace(); };
    }

    public void run(String[] args) throws Exception {

	LongOpt[] longopts = new LongOpt[13];
	params = new Properties();

	longopts[0] = new LongOpt("help", LongOpt.NO_ARGUMENT, null, 'h');
	longopts[1] = new LongOpt("output", LongOpt.REQUIRED_ARGUMENT, null, 'o');
	longopts[2] = new LongOpt("alignment", LongOpt.REQUIRED_ARGUMENT, null, 'a');
	longopts[3] = new LongOpt("renderer", LongOpt.REQUIRED_ARGUMENT, null, 'r');
	longopts[4] = new LongOpt("debug", LongOpt.OPTIONAL_ARGUMENT, null, 'd');
	longopts[5] = new LongOpt("impl", LongOpt.REQUIRED_ARGUMENT, null, 'i');
	longopts[6] = new LongOpt("params", LongOpt.REQUIRED_ARGUMENT, null, 'p');
	longopts[7] = new LongOpt("name", LongOpt.REQUIRED_ARGUMENT, null, 'n');
	longopts[8] = new LongOpt("prefix", LongOpt.REQUIRED_ARGUMENT, null, 'u');
	longopts[9] = new LongOpt("source", LongOpt.REQUIRED_ARGUMENT, null, 's');
	longopts[10] = new LongOpt("target", LongOpt.REQUIRED_ARGUMENT, null, 't');
	// Is there a way for that in LongOpt ???
	longopts[11] = new LongOpt("D", LongOpt.REQUIRED_ARGUMENT, null, 'D');
	longopts[12] = new LongOpt("directory", LongOpt.REQUIRED_ARGUMENT, null, 'w');

	Getopt g = new Getopt("", args, "ho:a:d::n:u:r:i:s:t:p:D:w:", longopts);
	int c = g.getopt();
	String arg = "";

	while (c != -1) {
		this.runSwitch( c, g, arg);
		c = g.getopt();
	}
	//int i = g.getOptind();
		this.runIFMethod();
	iterateDirectories();
    }

	/**
	 *
	 */
	private void runIFMethod(){
		if (debug == 0 && params.getProperty("debug") != null) {
			debug = Integer.parseInt(params.getProperty("debug"));
		}
		if (debug > 0) params.setProperty( "debug", Integer.toString( debug-1 ) );
	}

	/**
	 *
	 * @param c
	 * @param g
	 * @param arg
	 * @throws IOException
	 */
    private void runSwitch(int c, Getopt g, String arg) throws IOException {
		switch (c) {
			case 'h' :
				usage();
				return;
		//	case 'o' :
		/* Write output in given filename */
		//		filename = g.getOptarg();
		//		break;
			case 'n' :
				arg = g.getOptarg();
		/* Use common ontology to compare */
		this.runSwitchInner(arg, g);
				break;
			case 'p' :
		/* Read parameters from filename */
				paramfile = g.getOptarg();
				params.loadFromXML( new FileInputStream( paramfile ) );
				break;
		//	case 'i' :
		/* Use the given class for the alignment */
		//		alignmentClassName = g.getOptarg();
		//		break;
			case 'a' :
		/* Use the given file as a partial alignment */
				initName = g.getOptarg();
				break;
		//	case 't' :
		/* Use the given filename for target ontology */
		//		target = g.getOptarg();
		//		break;
			case 'D' :
		/* Parameter definition */
				arg = g.getOptarg();
				int index = arg.indexOf('=');
				this.runSwitchInnerB(index, arg, g);
				break;
			case 'd' :
		/* Debug level  */
				arg = g.getOptarg();
				this.runSwitchInnerC(arg);
				break;
			case 'w' :
		/* Use the given ontology directory */
				arg = g.getOptarg();
				this.runSwitchInnerD(arg, g);
				break;
		}
	}


	/**
	 *
	 * @param arg
	 * @param g
	 */
	private void runSwitchInner(String arg, Getopt g){
		if(arg!=null){
			try { uri1 = new URI(g.getOptarg());
			} catch (Exception e) { e.printStackTrace(); }
		}
		else{uri1 = null;}
	}

	/**
	 *
	 * @param index
	 * @param arg
	 * @param g
	 */
	private void runSwitchInnerB( int index, String arg, Getopt g){
		if ( index != -1 ) {
			params.setProperty( arg.substring( 0, index),
					arg.substring(index+1));
		} else {
			System.err.println("Bad parameter syntax: "+g);
			usage();
			System.exit(0);
		}
	}

	/**
	 *
	 * @param arg
	 */
	private void runSwitchInnerC( String arg){
		if ( arg != null ) debug = Integer.parseInt(arg.trim());
		else debug = 4;
	}

	/**
	 *
	 * @param arg
	 * @param g
	 */
	private void runSwitchInnerD(String arg, Getopt g){
		if ( arg != null ) ontoDir = g.getOptarg();
		else ontoDir = null;
	}

	public void iterateDirectories (){
	File [] subdir = null;
	try { 
		if(ontoDir == null){
		    subdir = (new File(System.getProperty("user.dir"))).listFiles(); 
		}
		else{
			subdir = (new File(ontoDir)).listFiles();
		}
	} catch ( Exception e ) {
	    System.err.println("Cannot stat dir "+ e.getMessage());
	    usage();
	}
	int size = subdir.length;
	for ( int i=0 ; i < size; i++ ) {
	    if( subdir[i].isDirectory() ) {
		// Align
		if ( debug > 0 ) System.err.println("Directory: "+subdir[i]);
		align( subdir[i] );
	    }
	}
    }

    public void align ( File dir ) {
	String prefix = null;
	AlignmentProcess result = null;
	Alignment init = null;
	PrintWriter writer = null;

	this.alignIFMethodA(dir, prefix);

	//System.err.println("Here it is "+prefix+" (end by /?)");

	try {
		this.alignIFMethodB(dir, prefix);
	    URI uri2 = new URI(prefix+target);
	    this.alignIFMethodC(uri2);
	    try {
	    	this.alignIFMethodD(init, uri2);


		// Create alignment object
		Object[] mparams = {};
		Class[] cparams = {};
		Class<?> alignmentClass = Class.forName(alignmentClassName);
		java.lang.reflect.Constructor alignmentConstructor =
		    alignmentClass.getConstructor(cparams);
		result = (AlignmentProcess)alignmentConstructor.newInstance(mparams);
		result.init( uri1, uri2 );
	    } catch (Exception ex) {
		System.err.println("Cannot create alignment "+ alignmentClassName+ "\n"+ ex.getMessage());
		throw ex;
	    }
	    this.alignIFMethodE(result, init);
	    // Set output file
	    writer = new PrintWriter (
                         new BufferedWriter(
                             new OutputStreamWriter( 
                                 new FileOutputStream(dir+File.separator+filename+".rdf"), "UTF-8" )), true);
	    AlignmentVisitor renderer = null;

	    try {
		Object[] mparams = { (Object)writer };
		Class[] cparams = { Class.forName("java.io.PrintWriter") };
		java.lang.reflect.Constructor rendererConstructor =
		    Class.forName(rendererClass).getConstructor(cparams);
		renderer = (AlignmentVisitor)rendererConstructor.newInstance(mparams);
	    } catch (Exception ex) {
		System.err.println("Cannot create renderer "+rendererClass+"\n"+ ex.getMessage());
		throw ex;
	    }

	    this.alignIFMethodF(renderer, dir, result);


	} catch (Exception ex) {
		logger.error("FATAL ERROR", ex);
		this.alignIFMethodG(ex);


	} finally {
	    // JE: This instruction is very important
	    if ( writer != null ) writer.close();
	    // Unload the ontologies
	    try { OntologyFactory.clear(); } catch (Exception e) {};
	}
    }

	/**
	 *
	 * @param result
	 * @param init
	 */
	private void alignIFMethodE(AlignmentProcess result, Alignment init) {
		if (debug > 1) System.err.println(" Alignment structure created");

		// Compute alignment
		long time = System.currentTimeMillis();
		try {
			result.align(init, params); // add opts
		} catch (AlignmentException e) {
			e.printStackTrace();
		}
		long newTime = System.currentTimeMillis();
		result.setExtension( Namespace.ALIGNMENT.uri, Annotations.TIME, Long.toString(newTime - time) );
		if (debug > 1) System.err.println(" Alignment performed");
	}

	/**
	 *
	 * @param renderer
	 * @param dir
	 * @param result
	 */
	private void alignIFMethodF(AlignmentVisitor renderer, File dir, AlignmentProcess result) {
		if (debug > 1) System.err.println(" Outputing result to "+dir+File.separator+filename+".rdf");
		// Output
		try {
			result.render( renderer);
		} catch (AlignmentException e) {
			e.printStackTrace();
		}
		if (debug > 1) System.err.println(" Done..."+renderer+"\n");
	}

	/**
	 *
	 * @param ex
	 */
	private void alignIFMethodG(Exception ex){
		if ( debug > 1 ) ex.printStackTrace();
	}

	/**
	 *
	 * @param dir
	 * @param prefix
	 */
	private void alignIFMethodA(File dir, String prefix){
		if ( urlprefix != null ){
			prefix = urlprefix+"/"+dir.getName()+"/";
		} else {
			// sounds like the only way to have something portable
			// This is the modification for acomodating the HCONE
			prefix = dir.toURI().toString();
			// The problem is that is brings
			// file:/localpath
			// instead of
			// file://localhost/localpath
			// Apparently should be file:///c:/localpath
		}
	}

	/**
	 *
	 * @param dir
	 * @param prefix
	 * @throws URISyntaxException
	 */
	private void alignIFMethodB(File dir, String prefix) throws URISyntaxException {
		if ( !source.equalsIgnoreCase("onto1.rdf")
				&& !target.equalsIgnoreCase("onto1.rdf") ) {
			uri1 = new URI(prefix+source);
		} else if ( uri1 == null ) uri1 = new URI(prefix+source);
	}

	/**
	 *
	 * @param uri2
	 */
	private void alignIFMethodC(URI uri2){
		if (debug > 1) System.err.println(" Handler set");
		if (debug > 1) System.err.println(" URI1: "+uri1);
		if (debug > 1) System.err.println(" URI2: "+uri2);
	}

	/**
	 *
	 * @param init
	 * @param uri2
	 * @throws AlignmentException
	 */
	private void alignIFMethodD(Alignment init, URI uri2) throws AlignmentException {
		if (initName != null) {
			AlignmentParser aparser = new AlignmentParser(debug-1);
			init = aparser.parse( initName );
			uri1 = init.getFile1();
			uri2 = init.getFile2();
			if (debug > 1) System.err.println(" Init parsed");
		}
	}



    public void usage() {
	System.err.println("usage: GroupAlign [options]");
	System.err.println("options are:");
	System.err.println("\t--name=uri -n uri\t\tUse the given uri to compare with.");
	System.err.println("\t--source=filename -s filename Source filename (default onto1.rdf)");
	System.err.println("\t--target=filename -t filename Target filename (default onto.rdf)");
	System.err.println("\t--prefix=uriprefix -u uriprefix URI prefix of the target");
	System.err.println("\t--output=filename -o filename\tOutput the alignment in filename.rdf");
	System.err.println("\t--impl=className -i classname\t\tUse the given alignment implementation.");
	System.err.println("\t--renderer=className -r className\tSpecifies the alignment renderer");
	System.err.println("\t--alignment=filename -a filename Start from an XML alignment file");
	System.err.println("\t--params=filename -p filename\tReads parameters from filename");
	System.err.println("\t-Dparam=value\t\t\tSet parameter");
	System.err.println("\t--debug[=n] -d [n]\t\tReport debug info at level n");
	System.err.println("\t--help -h\t\t\tPrint this message");
	System.err.print("\n"+GroupAlign.class.getPackage().getImplementationTitle()+" "+GroupAlign.class.getPackage().getImplementationVersion());
	System.err.println(" ($Id: GroupAlign.java 1820 2013-03-06 10:13:00Z euzenat $)\n");
    }
}
