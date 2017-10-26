/*
 * $Id: OWLAxiomsRendererVisitor.java 1827 2013-03-07 22:44:05Z euzenat $
 *
 * Copyright (C) INRIA, 2003-2004, 2007-2013
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation; either version 2.1 of the License, or
 * (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 * 
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307 USA
 */

package fr.inrialpes.exmo.align.impl.renderer; 

import java.util.Properties;
import java.io.PrintWriter;
import java.net.URI;

import fr.inrialpes.exmo.align.impl.*;
import fr.inrialpes.exmo.align.impl.rel.*;
import org.semanticweb.owl.align.Alignment;
import org.semanticweb.owl.align.AlignmentVisitor;
import org.semanticweb.owl.align.AlignmentException;
import org.semanticweb.owl.align.Cell;
import org.semanticweb.owl.align.Relation;

import fr.inrialpes.exmo.ontowrap.LoadedOntology;
import fr.inrialpes.exmo.ontowrap.OntowrapException;

import fr.inrialpes.exmo.align.parser.SyntaxElement;
import fr.inrialpes.exmo.align.parser.SyntaxElement.Constructor;

import fr.inrialpes.exmo.align.impl.edoal.PathExpression;
import fr.inrialpes.exmo.align.impl.edoal.Expression;
import fr.inrialpes.exmo.align.impl.edoal.ClassExpression;
import fr.inrialpes.exmo.align.impl.edoal.ClassId;
import fr.inrialpes.exmo.align.impl.edoal.ClassConstruction;
import fr.inrialpes.exmo.align.impl.edoal.ClassTypeRestriction;
import fr.inrialpes.exmo.align.impl.edoal.ClassDomainRestriction;
import fr.inrialpes.exmo.align.impl.edoal.ClassValueRestriction;
import fr.inrialpes.exmo.align.impl.edoal.ClassOccurenceRestriction;
import fr.inrialpes.exmo.align.impl.edoal.PropertyExpression;
import fr.inrialpes.exmo.align.impl.edoal.PropertyId;
import fr.inrialpes.exmo.align.impl.edoal.PropertyConstruction;
import fr.inrialpes.exmo.align.impl.edoal.PropertyDomainRestriction;
import fr.inrialpes.exmo.align.impl.edoal.PropertyTypeRestriction;
import fr.inrialpes.exmo.align.impl.edoal.PropertyValueRestriction;
import fr.inrialpes.exmo.align.impl.edoal.RelationExpression;
import fr.inrialpes.exmo.align.impl.edoal.RelationId;
import fr.inrialpes.exmo.align.impl.edoal.RelationConstruction;
import fr.inrialpes.exmo.align.impl.edoal.RelationDomainRestriction;
import fr.inrialpes.exmo.align.impl.edoal.RelationCoDomainRestriction;
import fr.inrialpes.exmo.align.impl.edoal.InstanceExpression;
import fr.inrialpes.exmo.align.impl.edoal.InstanceId;

import fr.inrialpes.exmo.align.impl.edoal.Transformation;
import fr.inrialpes.exmo.align.impl.edoal.ValueExpression;
import fr.inrialpes.exmo.align.impl.edoal.Value;
import fr.inrialpes.exmo.align.impl.edoal.Apply;
import fr.inrialpes.exmo.align.impl.edoal.Datatype;
import fr.inrialpes.exmo.align.impl.edoal.Comparator;
import fr.inrialpes.exmo.align.impl.edoal.EDOALCell;
import fr.inrialpes.exmo.align.impl.edoal.EDOALAlignment;
import fr.inrialpes.exmo.align.impl.edoal.EDOALVisitor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Renders an alignment as a new ontology merging these.
 *
 * @author Jérôme Euzenat
 * @version $Id: OWLAxiomsRendererVisitor.java 1827 2013-03-07 22:44:05Z euzenat $ 
 */

public class OWLAxiomsRendererVisitor extends IndentedRendererVisitor implements AlignmentVisitor, EDOALVisitor {
    final static Logger logger = LoggerFactory.getLogger(OWLAxiomsRendererVisitor.class);
	boolean heterogeneous = false;
    boolean edoal = false;
    Alignment alignment = null;
    LoadedOntology onto1 = null;
    LoadedOntology onto2 = null;
    Cell cell = null;
    Relation toProcess = null;

    private static Namespace DEF = Namespace.ALIGNMENT;
    
    public OWLAxiomsRendererVisitor( PrintWriter writer ){
	super( writer );
    }

    public void init( Properties p ) {
	if ( p.getProperty("heterogeneous") != null ) heterogeneous = true;
    };

    public void instanceOf(Alignment align) throws AlignmentException {
		if ( align instanceof ObjectAlignment ) {
			alignment = align;
			onto1 = (LoadedOntology)((ObjectAlignment)alignment).getOntologyObject1();
			onto2 = (LoadedOntology)((ObjectAlignment)alignment).getOntologyObject2();
		} else if ( align instanceof EDOALAlignment ) {
			edoal = true;
		} else {
			try {
				alignment = AlignmentTransformer.toObjectAlignment((URIAlignment) align);
				onto1 = (LoadedOntology)((ObjectAlignment)alignment).getOntologyObject1();
				onto2 = (LoadedOntology)((ObjectAlignment)alignment).getOntologyObject2();
			} catch ( AlignmentException alex ) {
				throw new AlignmentException("OWLAxiomsRenderer: cannot render simple alignment. Need an ObjectAlignment", alex );
			}
		}
	}

	public void printExt(Alignment align){
		for ( String[] ext : align.getExtensions() ){
			writer.print("    <rdfs:comment>"+ext[1]+": "+ext[2]+"</rdfs:comment>"+NL);
		}
	}

	public void forMethod(Alignment align) throws OntowrapException {
		for( Cell c : align ){
			Object ob1 = c.getObject1();
			Object ob2 = c.getObject2();
			controlMethod(ob1,ob2);
		}
	}

	public void controlMethod(Object ob1, Object ob2 ) throws OntowrapException {

		if ( heterogeneous || edoal ||
				( onto1.isClass( ob1 ) && onto2.isClass( ob2 ) ) ||
				( onto1.isDataProperty( ob1 ) && onto2.isDataProperty( ob2 ) ) ||
				( onto1.isObjectProperty( ob1 ) && onto2.isObjectProperty( ob2 ) ) ||
				( onto1.isIndividual( ob1 ) && onto2.isIndividual( ob2 ) ) ) {
			//c.accept( this );
		}
	}

    public void visit( Alignment align ) throws AlignmentException {
	if ( subsumedInvocableMethod( this, align, Alignment.class ) ) return;
	// default behaviour

		this.instanceOf(align);
	writer.print("<rdf:RDF"+NL);
	writer.print("    xmlns:owl=\"http://www.w3.org/2002/07/owl#\""+NL);
	writer.print("    xmlns:rdf=\"http://www.w3.org/1999/02/22-rdf-syntax-ns#\""+NL);
	writer.print("    xmlns:rdfs=\"http://www.w3.org/2000/01/rdf-schema#\" "+NL);
	writer.print("    xmlns:xsd=\"http://www.w3.org/2001/XMLSchema#\">"+NL+NL);	
	writer.print("  <owl:Ontology rdf:about=\"\">"+NL);
	writer.print("    <rdfs:comment>Matched ontologies</rdfs:comment>"+NL);
	writer.print("    <rdfs:comment>Generated by fr.inrialpes.exmo.align.renderer.OWLAxiomsRendererVisitor</rdfs:comment>"+NL);
	this.printExt(align);
	writer.print("    <owl:imports rdf:resource=\""+align.getOntology1URI().toString()+"\"/>"+NL);
	writer.print("    <owl:imports rdf:resource=\""+align.getOntology2URI().toString()+"\"/>"+NL);
	writer.print("  </owl:Ontology>"+NL+NL);

		//forMethod(align);

		writer.print("</rdf:RDF>"+NL);
    }

    public void CMethod(Relation rel , Object ob1, URI u1) throws AlignmentException, OntowrapException {
		if ( ob1 instanceof InstanceExpression || onto1.isIndividual( ob1 ) ) {
			writer.print("  <owl:Thing rdf:about=\"" + u1 + "\">" + NL);
			rel.accept(this);
			writer.print("  </owl:Thing>" + NL);
		}
	}

	public void BMethod(Object ob1, Relation rel, URI u1) throws AlignmentException, OntowrapException {
		if ( ob1 instanceof RelationExpression || onto1.isObjectProperty( ob1 ) ) {
			writer.print("  <owl:ObjectProperty rdf:about=\"" + u1 + "\">" + NL);
			rel.accept(this);
			writer.print("  </owl:ObjectProperty>" + NL);
		}else {
			this.CMethod(rel,ob1,u1);
		}
	}

    public void AMethod(Object ob1, Relation rel, URI u1) throws AlignmentException, OntowrapException {
		if (ob1 instanceof PropertyExpression || onto1.isDataProperty(ob1)) {
			writer.print("  <owl:DatatypeProperty rdf:about=\"" + u1 + "\">" + NL);
			rel.accept(this);
			writer.print("  </owl:DatatypeProperty>" + NL);
		}else {
			this.BMethod(ob1,rel,u1);
		}
	}

    public void instanceOb1(Relation rel, Object ob1, URI u1) throws AlignmentException, OntowrapException {
		if ( ob1 instanceof ClassExpression || onto1.isClass( ob1 ) ) {
			writer.print("  <owl:Class rdf:about=\""+u1+"\">"+NL);
			rel.accept( this );
			writer.print("  </owl:Class>"+NL);
		} else {
			this.AMethod(ob1,rel,u1);
		}
	}

    public void controlRel(Relation rel, URI u1, Object ob2, Object ob1) throws OntowrapException, AlignmentException {
		if ( rel instanceof SubsumeRelation || rel instanceof HasInstanceRelation ){
			u1 = onto2.getEntityURI( ob2 );
		} else {
			u1 = onto1.getEntityURI( ob1 );
		}
		this.instanceOb1(rel,ob1,u1);
	}

    public void ifMethod(Cell cell) throws AlignmentException, OntowrapException {
		if ( cell instanceof EDOALCell ) {
			cell.accept( this ); // useless cast?
		} else {
			this.cell = cell;
			Object ob1 = cell.getObject1();
			Object ob2 = cell.getObject2();
			URI u1=null;
			Relation rel = cell.getRelation();
			this.controlRel(rel,u1,ob2,ob1);
		}
	}

    public void visit( Cell cell ) throws AlignmentException {
	if ( subsumedInvocableMethod( this, cell, Cell.class ) ) return;
	// default behaviour
	if ( cell.getId() != null ) writer.print(NL+NL+"<!-- "+cell.getId()+" -->"+NL);
		try {
			this.ifMethod(cell);
		} catch (OntowrapException e) {
			e.printStackTrace();
		}
	}

    public void visit( EDOALCell cell ) throws AlignmentException {
	this.cell = cell;
	toProcess = cell.getRelation();
	increaseIndent();
	if ( toProcess instanceof SubsumeRelation || toProcess instanceof HasInstanceRelation ) {
	    ((Expression)cell.getObject2()).accept( this );
	} else {
	    ((Expression)cell.getObject1()).accept( this );
	}
	decreaseIndent();
	writer.print(NL);
    }

    // Classical dispatch
    // This is the previous code... which is the one which was used.
    // It should be reintroduced in the dispatch!
    public void visit( Relation rel ) throws AlignmentException {
	if ( subsumedInvocableMethod( this, rel, Relation.class ) ) return;
	// default behaviour
	Object ob2 = cell.getObject2();
	if ( edoal ) {
	    String owlrel = getRelationName( rel, ob2 );
	    if ( owlrel == null ) throw new AlignmentException( "Relation "+rel+" cannot apply to "+ob2 );
	    writer.print("  <"+owlrel+">"+NL);
	    increaseIndent();
	    if ( rel instanceof HasInstanceRelation || rel instanceof SubsumeRelation ) {
		((Expression)cell.getObject1()).accept( this );
	    } else {
		((Expression)ob2).accept( this );
	    }
	    decreaseIndent();
	    writer.print(NL+"  </"+owlrel+">");
	} else {
	    String owlrel = getRelationName( onto2, rel, ob2 );
	    if ( owlrel == null ) throw new AlignmentException( "Cannot express relation "+rel );
	    try {
		writer.print("    <"+owlrel+" rdf:resource=\""+onto2.getEntityURI( ob2 )+"\"/>"+NL);
	    } catch ( OntowrapException owex ) {
		throw new AlignmentException( "Error accessing ontology", owex );
	    }
	}
    }

    public void printRel( Object ob, LoadedOntology onto, Relation rel ) throws AlignmentException {
	if ( !edoal ) {
	    String owlrel = getRelationName( onto, rel, ob );
	    if ( owlrel == null ) throw new AlignmentException( "Cannot express relation "+rel );
	    try {
		writer.print("    <"+owlrel+" rdf:resource=\""+onto.getEntityURI( ob )+"\"/>"+NL);
	    } catch ( OntowrapException owex ) {
		throw new AlignmentException( "Error accessing ontology", owex );
	    }
	} else {
	    String owlrel = getRelationName( rel, ob );
	    if ( owlrel == null ) throw new AlignmentException( "Cannot express relation "+rel );
	    if ( ob instanceof InstanceId ) {
		indentedOutput("<"+owlrel+" rdf:resource=\""+((InstanceId)ob).getURI()+"\"/>");
	    } else {
		indentedOutput("<"+owlrel+">");
		writer.print(NL);
		increaseIndent();
		((Expression)ob).accept( this ); // ?? no cast
		decreaseIndent();
		writer.print(NL);
		indentedOutput("</"+owlrel+">");
	    }
	}
    }

    /**
     * For EDOAL relation name depends on type of expressions
     */
    // The two getRelationName may be put as relation methods (this would be more customisable)
    public String getRelationName( Relation rel, Object ob ) {
		return this.classInstanceA(rel, ob);
	}

    public String classInstanceA(Relation rel, Object ob){
		if ( rel instanceof EquivRelation ) {
			if ( ob instanceof ClassExpression ) {
				return "owl:equivalentClass";
			} else if ( ob instanceof PropertyExpression || ob instanceof RelationExpression ) {
				return "owl:equivalentProperty";
			} else if ( ob instanceof InstanceExpression ) {
				return "owl:sameAs";
			}
		}else{
			this.classInstanceB(rel, ob);
		}
		return "owl:subPropertyOf";
	}

	public String classInstanceB(Relation rel, Object ob){
		if ( rel instanceof IncompatRelation ) {
			if ( ob instanceof ClassExpression ) {
				return "owl:disjointFrom";
			} else if ( ob instanceof InstanceExpression ) {
				return "owl:differentFrom";
			}
		}else{
			this.classInstanceC(rel, ob);
		}
		return "owl:subPropertyOf";
	}

	public String classInstanceC(Relation rel, Object ob){
		if ( rel instanceof SubsumeRelation ) {
			//reversed = true;
			if ( ob instanceof ClassExpression ) {
				return "owl:subClassOf";
			} else if ( ob instanceof PropertyExpression || ob instanceof RelationExpression ) {
				return "owl:subPropertyOf";
			}
		}else{
			this.classInstanceD(rel, ob);
		}
		return "owl:subPropertyOf";
	}

	public String classInstanceD(Relation rel, Object ob){
		if ( rel instanceof SubsumedRelation ) {
			if ( ob instanceof ClassExpression ) {
				return "owl:subClassOf";
			}
		}else{
			this.classInstanceE(rel, ob);
		}

		return "owl:subClassOf";
	}

	public String classInstanceE(Relation rel, Object ob){
		if ( rel instanceof InstanceOfRelation ) {
			if ( ob instanceof InstanceExpression ) {
				return "rdf:type";
			}
		}else{
			this.classInstanceF(rel, ob);
		}
		return "rdf:type";
	}


	public String classInstanceF(Relation rel, Object ob){
		if (rel instanceof HasInstanceRelation) {
			return "";
		}else{
			return  "//";
		}
	}

    /**
     * Regular: relation name depends on loaded ontology
     */
    public String getRelationName( LoadedOntology onto, Relation rel, Object ob ) {
		return this.relationMethodA(onto, rel, ob) ;

    }

    public String relationMethodA(LoadedOntology onto, Relation rel, Object ob){
		try {
    	if ( rel instanceof EquivRelation ) {
			if ( onto.isClass( ob ) ) {
				return "owl:equivalentClass";
			} else if ( onto.isProperty( ob ) ) {
				return "owl:equivalentProperty";
			} else if ( onto.isIndividual( ob ) ) {
				return "owl:sameAs";
			}
		}else{
			return this.relationMethodB(onto, rel, ob);
		}
		} catch ( OntowrapException owex ) {
			logger.error("FATAL ERROR",owex);
		};
		return "rdf:type";
	}

	public String relationMethodB(LoadedOntology onto, Relation rel, Object ob){
		try {
			if ( rel instanceof SubsumeRelation ) {
				if ( onto.isClass( ob ) ) {
					return "rdfs:subClassOf";
				} else if ( onto.isProperty( ob ) ) {
					return "rdfs:subPropertyOf";
				}
			}else{
				return this.relationMethodC(onto, rel, ob);
			}
		} catch ( OntowrapException owex ) {
			logger.error("FATAL ERROR",owex);
		};
		return "rdf:type";
	}

	public String relationMethodC(LoadedOntology onto, Relation rel, Object ob){
		try {
			if ( rel instanceof IncompatRelation ) {
				if ( onto.isClass( ob ) ) {
					return "rdfs:disjointFrom";
				} else if ( onto.isIndividual( ob ) ) {
					return "owl:differentFrom";
				}
			}else{
				return this.relationMethodD(onto, rel, ob);
			}
		} catch ( OntowrapException owex ) {
			logger.error("FATAL ERROR",owex);
		};
		return "rdf:type";
	}

	public String relationMethodD(LoadedOntology onto, Relation rel, Object ob) throws OntowrapException {

			if ( rel instanceof InstanceOfRelation || rel instanceof HasInstanceRelation) {
				if ( onto.isClass( ob ) ) {
					return "rdf:type";
				}
			}
		return "rdf:type";
	}

    /* This may be genericised
       These methods are not used at the moment
       However they are roughly correct and may be used for more customisation
     */

    public void visit( EquivRelation rel ) throws AlignmentException {
	printRel( cell.getObject2(), onto2, rel );
    }

    public void visit( SubsumeRelation rel ) throws AlignmentException {
	printRel( cell.getObject1(), onto1, rel );
    }

    public void visit( SubsumedRelation rel ) throws AlignmentException {
	printRel( cell.getObject2(), onto2, rel );
    }

    public void visit( IncompatRelation rel ) throws AlignmentException {
	printRel( cell.getObject2(), onto2, rel );
    }

    public void visit( InstanceOfRelation rel ) throws AlignmentException {
	printRel( cell.getObject2(), onto2, rel );
    }

    public void visit( HasInstanceRelation rel ) throws AlignmentException {
	printRel( cell.getObject1(), onto1, rel );
    }

    // ******* EDOAL

    public void visit( final ClassId e ) throws AlignmentException {
	if ( toProcess == null ) {
	    indentedOutput("<owl:Class "+SyntaxElement.RDF_ABOUT.print(DEF)+"=\""+e.getURI()+"\"/>");
	} else {
	    Relation toProcessNext = toProcess;
	    toProcess = null;
	    indentedOutput("<owl:Class "+SyntaxElement.RDF_ABOUT.print(DEF)+"=\""+e.getURI()+"\">"+NL);
	    increaseIndent();
	    toProcessNext.accept( this );
	    writer.print(NL);
	    decreaseIndent();
	    indentedOutput("</owl:Class>");
	}
    }

    public void visitControlMethod(Relation toProcessNext, final ClassConstruction e, String owlop, final Constructor op) throws AlignmentException {
		if ( toProcessNext != null && e.getComponents().size() == 0 ) {
			if ( op.equals(Constructor.AND)) owlop = "http://www.w3.org/2002/07/owl#Thing";
			else if ( op.equals(Constructor.OR) ) owlop = "http://www.w3.org/2002/07/owl#Nothing";
			else if ( op.equals(Constructor.NOT)) throw new AlignmentException( "Complement constructor cannot be empty");
			indentedOutput("<owl:Class "+SyntaxElement.RDF_ABOUT.print(DEF)+"=\""+owlop+"\">"+NL);
			increaseIndent();
			toProcessNext.accept( this );
			writer.print(NL);
			decreaseIndent();
			indentedOutput("</owl:Class>");
		}else{
			this.visitControlMethod2(toProcessNext, e, owlop, op);
		}
	}

	public void visitControlMethod2(Relation toProcessNext, final ClassConstruction e, String owlop, final Constructor op) throws AlignmentException {
		if ( op.equals(Constructor.AND) ) owlop = "intersectionOf";
		else if ( op.equals(Constructor.OR)) owlop = "unionOf";
		else if ( op.equals(Constructor.NOT)) owlop = "complementOf";
		else throw new AlignmentException( "Unknown class constructor : "+op );
		if ( e.getComponents().size() == 0 ) {
			if (op.equals(Constructor.AND) ) indentedOutput("<owl:Thing/>");
			else if ( op.equals(Constructor.OR) ) indentedOutput("<owl:Nothing/>");
			else throw new AlignmentException( "Complement constructor cannot be empty");
		} else {
			this.visitControlMethod3(op, toProcessNext, e, owlop);
		}
	}

	public void visitControlMethod3(final Constructor op, Relation toProcessNext, final ClassConstruction e, String owlop) throws AlignmentException {
		indentedOutput("<owl:Class>"+NL);
		increaseIndent();
		indentedOutput("<owl:"+owlop);
		if ( ( op.equals(Constructor.AND) || op.equals(Constructor.OR) ) )
			writer.print(" "+SyntaxElement.RDF_PARSETYPE.print(DEF)+"=\"Collection\"");
		writer.print(">"+NL);
		increaseIndent();
		this.visitForMethod(e);
		decreaseIndent();
		indentedOutput("</owl:"+owlop+">"+NL);
		if ( toProcessNext != null ) { toProcessNext.accept( this ); writer.print(NL); }
		decreaseIndent();
		indentedOutput("</owl:Class>");
	}

	public void visitForMethod(final ClassConstruction e) throws AlignmentException {
		for (final ClassExpression ce : e.getComponents()) {
			writer.print(linePrefix);
			ce.accept( this );
			writer.print(NL);
		}
	}

    public void visit( final ClassConstruction e ) throws AlignmentException {

    	Relation toProcessNext = toProcess;
	toProcess = null;
	final Constructor op = e.getOperator();
	String owlop = null;
	// Very special treatment
	this.visitControlMethod(toProcessNext, e, owlop,op);
    }

    public void visit( final ClassValueRestriction c ) throws AlignmentException {
	Relation toProcessNext = toProcess;
	toProcess = null;
	indentedOutput("<owl:Restriction>"+NL);
	increaseIndent();
	indentedOutput("<owl:onProperty>"+NL);
	increaseIndent();
	c.getRestrictionPath().accept( this );
	decreaseIndent();
	writer.print(NL);
	indentedOutputln("</owl:onProperty>");
	ValueExpression ve = c.getValue();
	if ( ve instanceof Value ) {
	    indentedOutput("<owl:hasValue");
	    if ( ((Value)ve).getType() != null ) {
		writer.print( " rdf:datatype=\""+((Value)ve).getType()+"\"" );
	    }
	    writer.print( ">"+((Value)ve).getValue()+"</owl:hasValue>"+NL);
	} else if ( ve instanceof InstanceId ) {
	    indentedOutput("<owl:hasValue>"+NL);
	    increaseIndent();
	    ve.accept( this );
	    decreaseIndent();
	    writer.print(NL);
	    indentedOutput("</owl:hasValue>"+NL);
	} else throw new AlignmentException( "OWL does not support path constraints in hasValue : "+ve );
	if ( toProcessNext != null ) { toProcessNext.accept( this ); writer.print(NL); }
	decreaseIndent();
	indentedOutput("</owl:Restriction>");
    }

    public void visit( final ClassTypeRestriction c ) throws AlignmentException {
	Relation toProcessNext = toProcess;
	toProcess = null;
	indentedOutput("<owl:Restriction>"+NL);
	increaseIndent();
	indentedOutput("<owl:onProperty>"+NL);
	increaseIndent();
	c.getRestrictionPath().accept( this );
	writer.print(NL);
	decreaseIndent();
	indentedOutput("</owl:onProperty>"+NL);
	indentedOutput("<owl:allValuesFrom>"+NL);
	increaseIndent();
	c.getType().accept( this );
	writer.print(NL);
	decreaseIndent();
	indentedOutput("</owl:allValuesFrom>"+NL);
	if ( toProcessNext != null ) { toProcessNext.accept( this ); writer.print(NL); }
	decreaseIndent();
	indentedOutput("</owl:Restriction>");
    }

    public void visit( final ClassDomainRestriction c ) throws AlignmentException {
	Relation toProcessNext = toProcess;
	toProcess = null;
	indentedOutput("<owl:Restriction>"+NL);
	increaseIndent();
	indentedOutput("<owl:onProperty>"+NL);
	increaseIndent();
	c.getRestrictionPath().accept( this );
	writer.print(NL);
	decreaseIndent();
	indentedOutput("</owl:onProperty>"+NL);
	if ( c.isUniversal() ) {
	    indentedOutput("<owl:allValuesFrom>"+NL);
	} else {
	    indentedOutput("<owl:someValuesFrom>"+NL);
	}
	increaseIndent();
	c.getDomain().accept( this );
	writer.print(NL);
	decreaseIndent();
	if ( c.isUniversal() ) {
	    indentedOutput("</owl:allValuesFrom>"+NL);
	} else {
	    indentedOutput("</owl:someValuesFrom>"+NL);
	}
	if ( toProcessNext != null ) { toProcessNext.accept( this ); writer.print(NL); }
	decreaseIndent();
	indentedOutput("</owl:Restriction>");
    }

    // TOTEST
    public void visit( final ClassOccurenceRestriction c ) throws AlignmentException {
	Relation toProcessNext = toProcess;
	toProcess = null;
	indentedOutput("<owl:Restriction>"+NL);
	increaseIndent();
	indentedOutput("<owl:onProperty>"+NL);
	increaseIndent();
	c.getRestrictionPath().accept( this );
	writer.print(NL);
	decreaseIndent();
	indentedOutput("</owl:onProperty>"+NL);
	String cardinality = null;
	Comparator comp = c.getComparator();
	if ( comp == Comparator.EQUAL ) cardinality = "cardinality";
	else if ( comp == Comparator.LOWER ) cardinality = "maxCardinality";
	else if ( comp == Comparator.GREATER ) cardinality = "minCardinality";
	else throw new AlignmentException( "Unknown comparator : "+comp.getURI() );
	indentedOutput("<owl:"+cardinality+" rdf:datatype=\"&xsd;nonNegativeInteger\">");
	writer.print(c.getOccurence());
	writer.print("</owl:"+cardinality+">"+NL);
	if ( toProcessNext != null ) { toProcessNext.accept( this ); writer.print(NL); }
	decreaseIndent();
	indentedOutput("</owl:Restriction>");
    }
    
    public void visit(final PropertyId e) throws AlignmentException {
	if ( toProcess == null ) {
	    indentedOutput("<owl:DatatypeProperty "+SyntaxElement.RDF_ABOUT.print(DEF)+"=\""+e.getURI()+"\"/>");
	} else {
	    Relation toProcessNext = toProcess;
	    toProcess = null;
	    indentedOutput("<owl:DatatypeProperty "+SyntaxElement.RDF_ABOUT.print(DEF)+"=\""+e.getURI()+"\">"+NL);
	    increaseIndent();
	    toProcessNext.accept( this );
	    writer.print(NL);
	    decreaseIndent();
	    indentedOutput("</owl:DatatypeProperty>");
	}
    }

    /**
     * OWL, and in particular OWL 2, does not allow for more Relation (ObjectProperty)
     * and Property (DataProperty) constructor than owl:inverseOf
     * It is thus imposible to transcribe our and, or and not constructors.
     */

    public void ifElseMethod(final PropertyConstruction e, final Constructor op ) throws AlignmentException {
		if ( (op == Constructor.AND) || (op == Constructor.OR) || (op == Constructor.COMP) ) {
			for ( final PathExpression pe : e.getComponents() ) {
				writer.print(linePrefix);
				pe.accept( this );
				writer.print(NL);
			}
		}else{
			this.ifElseMethodB(e);
		}
	}

	public void ifElseMethodB(final PropertyConstruction e ) throws AlignmentException {
		for (final PathExpression pe : e.getComponents()) {
			pe.accept( this );
			writer.print(NL);
		}
	}

    public void visit( final PropertyConstruction e ) throws AlignmentException {
	Relation toProcessNext = toProcess;
	toProcess = null;
	indentedOutput("<owl:DatatypePropety>"+NL);
	increaseIndent();
	final Constructor op = e.getOperator();
	String owlop = null;
	if ( op == Constructor.COMP ) owlop = "propertyChainAxiom";
	// JE: FOR TESTING
	//owlop = "FORTESTING("+op.name()+")";
	if ( owlop == null ) throw new AlignmentException( "Cannot translate property construction in OWL : "+op );
	indentedOutput("<owl:"+owlop);
	if ( (op == Constructor.AND) || (op == Constructor.OR) || (op == Constructor.COMP) ) writer.print(" "+SyntaxElement.RDF_PARSETYPE.print(DEF)+"=\"Collection\"");
	writer.print(">"+NL);
	increaseIndent();
	this.ifElseMethod(e,op);
	decreaseIndent();
	indentedOutput("</owl:"+owlop+">"+NL);
	if ( toProcessNext != null ) { toProcessNext.accept( this ); writer.print(NL); }
	decreaseIndent();
	indentedOutput("</owl:DatatypePropety>");
    }
	
    public void visit(final PropertyValueRestriction c) throws AlignmentException {
	Relation toProcessNext = toProcess;
	toProcess = null;
	indentedOutput("<owl:DatatypeProperty>"+NL);
	increaseIndent();
	indentedOutput("<rdfs:range>"+NL);
	increaseIndent();
	indentedOutput("<rdfs:Datatype>"+NL);
	increaseIndent();
	indentedOutput("<owl:oneOf>"+NL);
	increaseIndent();
	// In EDOAL, this does only contain one value and is thus rendered as:
	indentedOutput("<rdf:Description>"+NL);
	increaseIndent();
	ValueExpression ve = c.getValue();
	if ( ve instanceof Value ) {
	    indentedOutput("<rdf:first");
	    if ( ((Value)ve).getType() != null ) {
		writer.print( " rdf:datatype=\""+((Value)ve).getType()+"\"" );
	    }
	    writer.print( ">"+((Value)ve).getValue()+"</rdf:first>"+NL);
	} else {
	    indentedOutput("<rdf:first>"+NL);
	    ve.accept( this );
	    writer.print("</rdf:first>"+NL);
	    indentedOutput("<rdf:rest rdf:resource=\"http://www.w3.org/1999/02/22-rdf-syntax-ns#nil\"/>"+NL);
	}
	decreaseIndent();
	indentedOutput("</rdf:Description>"+NL);
	// This is incorrect for more than one value... see the OWL:
	/*
         <rdfs:Datatype>
          <owl:oneOf>
           <rdf:Description>
            <rdf:first rdf:datatype="&xsd;integer">1</rdf:first>
             <rdf:rest>
              <rdf:Description>
               <rdf:first rdf:datatype="&xsd;integer">2</rdf:first>
               <rdf:rest rdf:resource="http://www.w3.org/1999/02/22-rdf-syntax-ns#nil"/>
              </rdf:Description>
             </rdf:rest>
            </rdf:Description>
           </owl:oneOf>
          </rdfs:Datatype>
	*/
	decreaseIndent();
	indentedOutput("</owl:oneOf>"+NL);
	decreaseIndent();
	indentedOutput("</rdfs:Datatype>"+NL);
	decreaseIndent();
	indentedOutput("</rdfs:range>"+NL);
	if ( toProcessNext != null ) { toProcessNext.accept( this ); writer.print(NL); }
	decreaseIndent();
	indentedOutput("</owl:DatatypeProperty>");
    }

    public void visit(final PropertyDomainRestriction c) throws AlignmentException {
	Relation toProcessNext = toProcess;
	toProcess = null;
	indentedOutput("<owl:DatatypeProperty>"+NL);
	increaseIndent();
	indentedOutput("<rdfs:domain>"+NL);
	increaseIndent();
	c.getDomain().accept( this );
	writer.print(NL);
	decreaseIndent();
	indentedOutput("</rdfs:domain>"+NL);
	if ( toProcessNext != null ) { toProcessNext.accept( this ); writer.print(NL); }
	decreaseIndent();
	indentedOutput("</owl:DatatypeProperty>");
    }

    public void visit(final PropertyTypeRestriction c) throws AlignmentException {
	Relation toProcessNext = toProcess;
	toProcess = null;
	indentedOutput("<owl:DatatypeProperty>"+NL);
	increaseIndent();
	indentedOutput("<rdfs:range>"+NL);
	increaseIndent();
	c.getType().accept( this );
	decreaseIndent();
	indentedOutput("</rdfs:range>"+NL);
	if ( toProcessNext != null ) { toProcessNext.accept( this ); writer.print(NL); }
	decreaseIndent();
	indentedOutput("</owl:DatatypeProperty>");
    }
	
    public void visit( final RelationId e ) throws AlignmentException {
	if ( toProcess == null ) {
	    indentedOutput("<owl:ObjectProperty "+SyntaxElement.RDF_ABOUT.print(DEF)+"=\""+e.getURI()+"\"/>");
	} else {
	    Relation toProcessNext = toProcess;
	    toProcess = null;
	    indentedOutput("<owl:ObjectProperty "+SyntaxElement.RDF_ABOUT.print(DEF)+"=\""+e.getURI()+"\">"+NL);
	    increaseIndent();
	    toProcessNext.accept( this );
	    writer.print(NL);
	    decreaseIndent();
	    indentedOutput("</owl:ObjectProperty>");
	}
    }

    /**
     * OWL, and in particular OWL 2, does not allow for more Relation (ObjectProperty)
     * and Property (DataProperty) constructor than owl:inverseOf
     * It is thus imposible to transcribe our and, or and not constructors.
     * Moreover, they have no constructor for the symmetric, transitive and reflexive
     * closure and the compositional closure (or composition) can only be obtained by
     * defining a property subsumed by this closure through an axiom.
     * It is also possible to rewrite the reflexive closures as axioms as well.
     * But the transitive closure can only be obtained through subsumption.
     */

    public void visitIfMethod(final Constructor op, String owlop){
		if ( op == Constructor.INVERSE ) {
			owlop = "inverseOf";
		} else if ( op == Constructor.COMP ) {
			owlop = "propertyChainAxiom";
		}
	}

	public void visitIfMethodB(final Constructor op, final RelationConstruction e) throws AlignmentException {
		if ( (op == Constructor.AND) || (op == Constructor.OR) || (op == Constructor.COMP) ) {
			this.visitForMethodBA(e);
		} else { // NOT... or else: enumerate them
			this.visitForMethodBB(e);
		}
	}

	public void visitForMethodBA(final RelationConstruction e) throws AlignmentException {
		for (final PathExpression re : e.getComponents()) {
			writer.print(linePrefix);
			re.accept( this );
			writer.print(NL);
		}
	}

	public void visitForMethodBB(final RelationConstruction e) throws AlignmentException {
		for (final PathExpression re : e.getComponents()) {
			re.accept( this );
			writer.print(NL);
		}
	}

    public void visit( final RelationConstruction e ) throws AlignmentException {
	Relation toProcessNext = toProcess;
	toProcess = null;
	indentedOutput("<owl:ObjectProperty>"+NL);
	increaseIndent();
	final Constructor op = e.getOperator();
	String owlop = null;
	this.visitIfMethod(op, owlop);
	// JE: FOR TESTING
	//owlop = "FORTESTING("+op.name()+")";
	if ( owlop == null ) throw new AlignmentException( "Cannot translate relation construction in OWL : "+op );
	indentedOutput("<owl:"+owlop);
	if ( (op == Constructor.OR) || (op == Constructor.AND) || (op == Constructor.COMP) ) writer.print(" "+SyntaxElement.RDF_PARSETYPE.print(DEF)+"=\"Collection\"");
	writer.print(">"+NL);
	increaseIndent();
	this.visitIfMethodB(op, e);
	decreaseIndent();
	indentedOutput("</owl:"+owlop+">"+NL);
	if ( toProcessNext != null ) { toProcessNext.accept( this ); writer.print(NL); }
	decreaseIndent();
	indentedOutput("</owl:ObjectProperty>");
    }
	
    public void visit(final RelationCoDomainRestriction c) throws AlignmentException {
	Relation toProcessNext = toProcess;
	toProcess = null;
	indentedOutput("<owl:ObjectProperty>"+NL);
	increaseIndent();
	indentedOutput("<rdfs:range>"+NL);
	increaseIndent();
	c.getCoDomain().accept( this );
	writer.print(NL);
	decreaseIndent();
	indentedOutput("</rdfs:range>"+NL);
	if ( toProcessNext != null ) { toProcessNext.accept( this ); writer.print(NL); }
	decreaseIndent();
	indentedOutput("</owl:ObjectProperty>");
    }

    public void visit(final RelationDomainRestriction c) throws AlignmentException {
	Relation toProcessNext = toProcess;
	toProcess = null;
	indentedOutput("<owl:ObjectProperty>"+NL);
	increaseIndent();
	indentedOutput("<rdfs:domain>"+NL);
	increaseIndent();
	c.getDomain().accept( this );
	writer.print(NL);
	decreaseIndent();
	indentedOutput("</rdfs:domain>"+NL);
	if ( toProcessNext != null ) { toProcessNext.accept( this ); writer.print(NL); }
	decreaseIndent();
	indentedOutput("</owl:ObjectProperty>");
    }

    public void visit( final InstanceId e ) throws AlignmentException {
	if ( toProcess == null ) {
	    indentedOutput("<owl:Individual "+SyntaxElement.RDF_ABOUT.print(DEF)+"=\""+e.getURI()+"\"/>");
	} else {
	    Relation toProcessNext = toProcess;
	    toProcess = null;
	    indentedOutput("<owl:Individual "+SyntaxElement.RDF_ABOUT.print(DEF)+"=\""+e.getURI()+"\">"+NL);
	    increaseIndent();
	    toProcessNext.accept( this );
	    writer.print(NL);
	    decreaseIndent();
	    indentedOutput("</owl:Individual>");
	}
    }

    // Unused: see ClassValueRestriction above
    public void visit( final Value e ) throws AlignmentException {
    }

    // OWL does not allow for function calls
    public void visit( final Apply e ) throws AlignmentException {
	throw new AlignmentException( "Cannot render function call in OWL "+e );
    }

    // Not implemented. We only ignore transformations in OWL
    public void visit( final Transformation transf ) throws AlignmentException {
    }

    /**
     * Our Datatypes are only strings identifying datatypes.
     * For OWL, they should be considered as built-in types because we do 
     * not know how to add other types.
     * Hence we could simply have used a rdfs:Datatype="<name>"
     *
     * OWL offers further possiblities, such as additional owl:withRestriction
     * clauses
     */
    public void visit( final Datatype e ) {
	indentedOutput("<owl:Datatype><owl:onDataType rdf:resource=\""+e.getType()+"\"/></owl:Datatype>");
    }

}
