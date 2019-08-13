/* NSC -- new Scala compiler
 * Copyright 2005-2011 LAMP/EPFL
 *
 * @author  Paul Phillips
 */

package treehugger

import PartialFunction._
import Flags._

/** A DSL for generating scala code.  The goal is that the
 *  code generating code should look a lot like the code it
 *  generates.
 */

trait TreehuggerDSLs { self: Forest =>
  
  import definitions._
  import self.{ scalaDot, stringToTermName }
  
  object treehuggerDSL {
    // Add a null check to a Tree => Tree function
    def nullSafe[T](f: Tree => Tree, ifNull: Tree): Tree => Tree =
      tree => IF (tree MEMBER_== NULL) THEN ifNull ELSE f(tree)

    // def returning[T](x: T)(f: T => Unit): T = util.returning(x)(f)

    object LIT extends (Any => Literal) {
      def apply(x: Any)   = Literal(Constant(x))
      def unapply(x: Any) = condOpt(x) { case Literal(Constant(value)) => value }
    }

    // You might think these could all be vals, but empirically I have found that
    // at least in the case of UNIT the compiler breaks if you re-use trees.
    // However we need stable identifiers to have attractive pattern matching.
    // So it's inconsistent until I devise a better way.
    val TRUE          = LIT(true)
    val FALSE         = LIT(false)
    val ZERO          = LIT(0)
    def NULL          = LIT(null)
    def UNIT          = LIT(())    
    val WILDCARD      = Ident(nme.WILDCARD)
    val SEQ_WILDCARD  = Ident(tpnme.WILDCARD_STAR)
    val NIL           = REF(NilModule)
    val NONE          = REF(NoneModule)
    val PARTIALLY     = Ident(PartiallyAppliedParam) setSymbol PartiallyAppliedParam

    object WILD {
      def empty               = Ident(nme.WILDCARD)
      def apply(tpe: Type)    = Ident(nme.WILDCARD) setType tpe
      def unapply(other: Any) = cond(other) { case Ident(nme.WILDCARD) => true }
    }

    def fn(lhs: Tree, op:   Name, args: Tree*)  =
      if (args.toList.isEmpty) Select(lhs, op)
      else Apply(Select(lhs, op), args.toList)
    def fn(lhs: Tree, op: Symbol, args: Tree*)  =
      if (args.toList.isEmpty) Select(lhs, op)
      else Apply(Select(lhs, op), args.toList)
    def infix(lhs: Tree, op:   Name, args: Tree*): Infix = Infix(lhs, op, args.toList)
    def infix(lhs: Tree, op: Symbol, args: Tree*): Infix = Infix(lhs, op, args.toList)

    def mkInfixOr(lhs: Tree, rhs: Tree) = infix(PAREN(lhs), Boolean_or, PAREN(rhs))
    def mkInfixAnd(lhs: Tree, rhs: Tree) = infix(PAREN(lhs), Boolean_and, PAREN(rhs))
    
    class TypeMethods(target: Type) {
      def TYPE_#(sym: Symbol, args: Type*): Type = typeRef(target, sym, args: _*)
      def TYPE_#(sym: Symbol, args: Iterable[Type]): Type = TYPE_#(sym, args.toList: _*)
      def TYPE_#(name: Name, args: Type*): Type =
        TYPE_#(RootClass.newClass(name), args: _*)
      def TYPE_#(name: Name, args: Iterable[Type]): Type = TYPE_#(name, args.toList: _*)

      def TYPE_OF(args: Type*): Type = appliedType(target, args: _*)
      def TYPE_OF(args: Iterable[Type]): Type = TYPE_OF(args.toList: _*)
      
      def TYPE_FORSOME(trees: Tree*): Type = ExistentialType(trees.toList, target)
      def TYPE_FORSOME(trees: Iterable[Tree]): Type = TYPE_FORSOME(trees.toList)

      def TYPE_=>(typ: Type) = TYPE_FUNCTION(target, typ)
      def TYPE_WITH(args: Type*): Type = makeRefinedType(target :: args.toList)
    }

    class TreeMethods(target: Tree) {
      /** logical/comparison ops **/
      def OR(other: Tree)  = mkInfixOr(target, other)
      def AND(other: Tree) = mkInfixAnd(target, other)

      /** Note - calling ANY_== in the matcher caused primitives to get boxed
       *  for the comparison, whereas looking up nme.EQ does not.  See #3570 for
       *  an example of how target.tpe can be non-null, yet it claims not to have
       *  a member called nme.EQ.  Not sure if that should happen, but we can be
       *  robust by dragging in Any regardless.
       */
      def MEMBER_== (other: Tree)   = {
        val opSym = NoSymbol // if (target.tpe == null) NoSymbol else target.tpe member nme.EQ
        if (opSym == NoSymbol) ANY_==(other)
        else infix(target, opSym, other)
      }
      // def ANY_EQ  (other: Tree)     = OBJ_EQ(other AS ObjectClass.tpe)
      def ANY_==  (other: Tree)     = infix(target, Any_==, other)
      def ANY_!=  (other: Tree)     = infix(target, Any_!=, other)
      def ANY_->  (other: Tree)     =
        infix(target, getMember(ArrowAssocClass, "->"), other)
      // def OBJ_==  (other: Tree)     = infix(target, Object_==, other)
      // def OBJ_!=  (other: Tree)     = infix(target, Object_!=, other)
      def OBJ_EQ  (other: Tree)     = infix(target, Object_eq, other)
      def OBJ_NE  (other: Tree)     = infix(target, Object_ne, other)

      def INT_|   (other: Tree)     = infix(target, getMember(IntClass, nme.OR), other)
      def INT_&   (other: Tree)     = infix(target, getMember(IntClass, nme.AND), other)
      def INT_>=  (other: Tree)     = infix(target, getMember(IntClass, nme.GE), other)
      def INT_==  (other: Tree)     = infix(target, getMember(IntClass, nme.EQ), other)
      def INT_!=  (other: Tree)     = infix(target, getMember(IntClass, nme.NE), other)
      def INT_<=  (other: Tree)     = infix(target, getMember(IntClass, nme.LE), other)
      def INT_<   (other: Tree)    = infix(target, getMember(IntClass, nme.LT), other)
      def INT_>   (other: Tree)    = infix(target, getMember(IntClass, nme.GT), other)
      def INT_TO  (other: Tree)    = infix(target, getMember(IntClass, "to"), other)
      def INT_+   (other: Tree)    = infix(target, getMember(IntClass, nme.ADD), other)
      def INT_-   (other: Tree)    = infix(target, getMember(IntClass, nme.MINUS), other)
      def INT_*   (other: Tree)    = infix(target, getMember(IntClass, nme.MUL), other)
      def INT_/   (other: Tree)    = infix(target, getMember(IntClass, nme.DIV), other)
      
      // def BOOL_&& (other: Tree)     = infix(target, Boolean_and, other)
      // def BOOL_|| (other: Tree)     = infix(target, Boolean_or, other)

      def OR_PATTERN(other: Tree)   = INFIXUNAPPLY("|", other)

      /** Apply, Select, Match **/
      def APPLY(params: Tree*): Apply          = Apply(target, params.toList)
      def APPLY(params: Iterable[Tree]): Apply = Apply(target, params.toList)
      def MATCH(cases: CaseDef*): Match        = Match(target, cases.toList)
      def MATCH(cases: Iterable[CaseDef]): Match = Match(target, cases.toList)

      def UNAPPLY(params: Tree*): UnApply      = UnApply(target, params.toList)
      def UNAPPLY(params: Iterable[Tree]): UnApply = UnApply(target, params.toList)
       
      def APPLYTYPE(typs: Type*)      = TypeApply(target, typs.toList map {TypeTree(_)})
      def APPLYTYPE(typs: Iterable[Type]) = TypeApply(target, typs.toList map {TypeTree(_)})
      
      def DOT(member: Name)         = SelectStart(Select(target, member))
      def DOT(sym: Symbol)          = SelectStart(Select(target, sym))

      def POSTFIX(name: Name): Infix  = infix(target, name, List[Tree](): _*)
      def POSTFIX(sym: Symbol): Infix = infix(target, sym, List[Tree](): _*)

      def INFIX(name: Name, param0: Tree, params: Tree*): Infix =
        infix(target, name, List(param0) ::: params.toList: _*)
      def INFIX(sym: Symbol, param0: Tree, params: Tree*): Infix =
        infix(target, sym, List(param0) ::: params.toList: _*)
      
      def INFIX(name: Name): InfixStart = InfixStart(target, name)
      def INFIX(sym: Symbol): InfixSymStart = InfixSymStart(target, sym)
            
      def INFIXUNAPPLY(name: Name, param0: Tree, params: Tree*)  = InfixUnApply(target, name, List(param0) ::: params.toList)
      def INFIXUNAPPLY(sym: Symbol, param0: Tree, params: Tree*) = InfixUnApply(target, sym, List(param0) ::: params.toList)
      
      def inPackage(name: Name): PackageDef = PACKAGEHEADER(name) := target
      def inPackage(sym: Symbol): PackageDef = PACKAGEHEADER(sym) := target
      def withoutPackage: PackageDef = PACKAGEHEADER(NoSymbol) := target
      
      def withComment(comments: String*): Commented = withComments(comments.toList)
      def withComments(comments: String*): Commented = withComments(comments.toList)
      def withComments(comments: Iterable[String]): Commented = Commented(NoMods, comments.toList, target)
      def withDoc(elems: DocElement*): Commented = mkScalaDoc(target, elems.toList)
      def withDoc(comments: Iterable[String], elems: DocElement*): Commented =
        mkScalaDoc(target, comments.toList.map( s => s: DocElement) ++ elems.toList)

      def withType(tp: Type): Typed = Typed(target, TypeTree(tp))

      def DO_WHILE(cond: Tree) = LabelDef(nme.DOkw, cond, target)
      def withBinder(sym: Symbol) = Bind(sym, target)
      def withBinder(name: Name)  = Bind(name, target)
      def withAnnots(annots: AnnotationInfo*): Typed = withAnnots(annots.toList)
      def withAnnots(annots: Iterable[AnnotationInfo]): Typed =
        withType(annotatedType(annots.toList, NoType))
      
      /** Assignment */
      def :=(rhs: Tree)            = Assign(target, rhs)

      def LIST_::(lhs: Tree) = lhs INFIX("::", target)
      def LIST_:::(lhs: Tree) = lhs INFIX(":::", target)
      def UNLIST_::(lhs: Tree) = lhs INFIX(ConsClass) UNAPPLY(target)

      def SEQ_++(rhs: Tree) = target INFIX("++", rhs)
      def SEQ_/:(lhs: Tree) = PAREN(lhs INFIX("/:", target))
      def SEQ_:\(rhs: Tree) = PAREN(target INFIX(":\\", rhs))

      val FOREACH: Tree => Tree = APPLYFUNC(Traversable_foreach) _
      val MAP: Tree => Tree = APPLYFUNC(Traversable_map) _
      val FILTER: Tree => Tree = APPLYFUNC(Traversable_filter) _
      val FLATMAP: Tree => Tree = APPLYFUNC(Traversable_flatMap) _
      val COLLECT: Tree => Tree = APPLYFUNC(Traversable_collect) _
      val FIND: Tree => Tree = APPLYFUNC(Traversable_find) _
      def SLICE(from: Tree, to: Tree) = (target DOT "slice")(from, to)
      def TAKE(n: Tree) = (target DOT Traversable_take)(n)
      def DROP(n: Tree) = (target DOT Traversable_drop)(n)
      val TAKEWHILE: Tree => Tree = APPLYFUNC(Traversable_takeWhile) _
      val DROPWHILE: Tree => Tree = APPLYFUNC(Traversable_dropWhile) _
      val WITHFILTER: Tree => Tree = APPLYFUNC(Traversable_withFilter) _
      val FILTERNOT: Tree => Tree = APPLYFUNC(Traversable_filterNot) _
      def SPLITAT(n: Tree) = (target DOT "splitAt")(n)
      val SPAN: Tree => Tree = APPLYFUNC(Traversable_span) _
      val PARTITION: Tree => Tree = APPLYFUNC(Traversable_partition) _
      val GROUPBY: Tree => Tree = APPLYFUNC(Traversable_groupBy) _
      val FORALL: Tree => Tree = APPLYFUNC(Traversable_forall) _
      val EXISTS: Tree => Tree = APPLYFUNC(Traversable_exists) _
      val COUNT: Tree => Tree = APPLYFUNC(Traversable_count) _
      val FOLDLEFT: Tree => Tree = APPLYFUNC(Traversable_foldLeft) _
      val FOLDRIGHT: Tree => Tree = APPLYFUNC(Traversable_foldRight) _
      val REDUCELEFT: Tree => Tree = APPLYFUNC(Traversable_reduceLeft) _
      val REDUCERIGHT: Tree => Tree = APPLYFUNC(Traversable_reduceRight) _
      
      def APPLYFUNC(sym: Symbol)(f: Tree): Tree = f match {
        case Block(stats, expr)
        if (stats ::: List(expr)) forall {_.isInstanceOf[CaseDef]} =>
          target INFIX(sym) APPLY f
        case AnonFunc(_, _, rhs: Block) => target INFIX(sym) APPLY f
        case _ => target DOT sym APPLY f
      }
      
      /** Casting & type tests -- working our way toward understanding exactly
       *  what differs between the different forms of IS and AS.
       *
       *  See ticket #2168 for one illustration of AS vs. AS_ANY.
       */
      def AS(tpe: Type)       = mkAsInstanceOf(target, tpe, any = true, wrapInApply = false)
      def IS(tpe: Type)       = mkIsInstanceOf(target, tpe, true, wrapInApply = false)
      // def IS_OBJ(tpe: Type)   = mkIsInstanceOf(target, tpe, false)

      def TOSTRING            = fn(target, nme.toString_)
      def GETCLASS            = fn(target, Object_getClass)
    }

    def INFIX_CHAIN(name: Name, tree: Tree, trees: Tree*): Tree = INFIX_CHAIN(name, tree :: trees.toList)
    def INFIX_CHAIN(name: Name, trees: Iterable[Tree]): Tree =
      trees.toList match {
        case Nil => EmptyTree
        case List(x) => x
        case xs => xs reduceLeft { (x, y) => x INFIX(name) APPLY y }
      }
    def INFIX_CHAIN(sym: Symbol, tree: Tree, trees: Tree*): Tree = INFIX_CHAIN(sym, tree :: trees.toList)
    def INFIX_CHAIN(sym: Symbol, trees: Iterable[Tree]): Tree =
      trees.toList match {
        case Nil => EmptyTree
        case List(x) => x
        case xs => xs reduceLeft { (x, y) => x INFIX(sym) APPLY y }
      }    

    case class InfixStart(target: Tree, name: Name) {
      def APPLY(args: Tree*): Infix            = APPLY(args.toList)
      def APPLY(args: Iterable[Tree]): Infix   = Infix(target, name, args.toList)
      def UNAPPLY(args: Tree*): InfixUnApply   = UNAPPLY(args.toList)
      def UNAPPLY(args: Iterable[Tree]): InfixUnApply = InfixUnApply(target, name, args.toList)
    }
    
    case class InfixSymStart(target: Tree, sym: Symbol) {
      def APPLY(args: Tree*): Infix          = APPLY(args.toList)
      def APPLY(args: Iterable[Tree]): Infix = Infix(target, sym, args.toList)

      def UNAPPLY(args: Tree*): InfixUnApply = UNAPPLY(args.toList)
      def UNAPPLY(args: Iterable[Tree]): InfixUnApply = InfixUnApply(target, sym, args.toList)
    }

    case class SelectStart(tree: Select) {
      def apply(args: Tree*): Apply = apply(args.toList)
      def apply(args: Iterable[Tree]): Apply = Apply(tree, args.toList)

      def empty = tree
    }
    
    case class SuperStart(tree: Super) {
      def APPLYTYPE(typ: Type): Super = Super(tree.qual, typ.toString.toTypeName)
      def APPLYTYPE(name: Name): Super = Super(tree.qual, name.toTypeName)
      def APPLYTYPE(name: String): Super = APPLYTYPE(name: Name)
      def empty = tree
    }

    case class CaseStart(pat: Tree, guard: Tree) {
      // def IF(g: Tree): CaseStart    = new CaseStart(pat, g)
      def ==>(body: Tree): CaseDef   = CaseDef(pat, guard, body)
    }
    
    trait DefStart[ResultTreeType <: Tree, RhsTreeType <: Tree] {
      def name: Name
      def defaultMods: Modifiers
      def defaultPos: Position

      // mkTree could accept EmptyTree
      def mkTree(rhs: Tree): ResultTreeType
      def :=(rhs: RhsTreeType): ResultTreeType
      final def empty: ResultTreeType = mkTree(EmptyTree)
      final def tree: ResultTreeType = empty

      private var _mods: Modifiers = null
      private var _pos: Position = null
      private var _annotations: List[AnnotationInfo] = Nil

      def withFlags(flags: Long*): this.type = {
        if (_mods == null)
          _mods = defaultMods

        _mods = flags.foldLeft(_mods)(_ | _)
        this
      }
      def withFlags(pin: PRIVATEWITHIN): this.type = {
        if (_mods == null)
          _mods = defaultMods

        _mods = _mods | Flags.PRIVATE
        _mods = Modifiers(_mods.flags, pin.name, _mods.annotations)
        this
      }
      def withAnnots(annot: AnnotationInfo*): this.type = withAnnots(annot.toList)
      def withAnnots(annot: Iterable[AnnotationInfo]): this.type = {
        if (_mods == null)
          _mods = defaultMods
        _mods = Modifiers(_mods.flags, _mods.privateWithin, _mods.annotations ::: annot.toList)
        this
      } 
      
      def withPos(pos: Position): this.type = {
        _pos = pos
        this
      }
      
      final def mods = if (_mods == null) defaultMods else _mods
      final def pos  = if (_pos == null) defaultPos else _pos
    }
    
    trait TptStart {
      def defaultTpt: Tree

      private var _tpt: Tree = null

      def withType(tp: Type): this.type = {
        _tpt = TypeTree(tp)
        this
      }
      
      final def tpt  = if (_tpt == null) defaultTpt else _tpt
    }
    
    trait TparamsStart {
      private var _tparams: List[TypeDef] = Nil
      
      def withTypeParams(tparam: TypeDef*): this.type = withTypeParams(tparam.toList)
      def withTypeParams(tparam: Iterable[TypeDef]): this.type = {
        _tparams = _tparams ::: tparam.toList
        this
      }
      
      final def tparams: List[TypeDef] = _tparams
    }
    
    trait VparamssStart {
      private var _vparamss: List[List[ValDef]] = Nil
      
      def withParams(param: ValDef*): this.type = withParams(param.toList)
      def withParams(param: Iterable[ValDef]): this.type = {
        if (_vparamss == List(Nil))
          _vparamss = List(param.toList)
        else 
          _vparamss = _vparamss ::: List(param.toList)
        this
      }
      
      def vparamss: List[List[ValDef]] = _vparamss
    }
    
    /** VODD, if it is not obvious, means ValOrDefDef.  This is the
     *  common code between a tree based on a pre-existing symbol and
     *  one being built from scratch.
     */
    trait VODDStart[ResultTreeType <: Tree, RhsTreeType <: Tree] extends DefStart[ResultTreeType, RhsTreeType] with TptStart
    
    trait SymVODDStart[ResultTreeType <: Tree, RhsTreeType <: Tree] extends VODDStart[ResultTreeType, RhsTreeType] {
      def sym: Symbol

      def name        = sym.name
      def defaultMods = Modifiers(sym.flags)
      def defaultTpt  = TypeTree() // setPos sym.pos.focus
      def defaultPos  = sym.pos

      def :=(rhs: RhsTreeType): ResultTreeType =
        mkTree(rhs) // setSymbol (sym resetFlag mods.flags)
    }
    trait ValCreator { self: VODDStart[ValDef, Tree] =>
      
      def mkTree(rhs: Tree): ValDef = ValDef(mods, name, tpt, rhs)
    }
    trait DefCreator { self: VODDStart[DefDef, Tree] with TparamsStart with VparamssStart  =>
      def mkTree(rhs: Tree): DefDef = DefDef(mods, name, tparams, vparamss, tpt, rhs)
    }
    trait ProcCreator { self: DefStart[ProcDef, Block] with TparamsStart with VparamssStart =>
      def mkTree(rhs: Tree): ProcDef = ProcDef(mods, name, tparams, vparamss, rhs)
    }

    class DefSymStart(val sym: Symbol) extends SymVODDStart[DefDef, Tree] with DefCreator with TparamsStart with VparamssStart
    class ValSymStart(val sym: Symbol) extends SymVODDStart[ValDef, Tree] with ValCreator
    class ProcSymStart(val sym: Symbol) extends DefStart[ProcDef, Block] with ProcCreator with TparamsStart with VparamssStart {
      def name        = sym.name      
      def defaultMods = Modifiers(sym.flags)
      def defaultPos  = sym.pos

      final def :=(rhs: Block): ProcDef =
        mkTree(rhs)
    }
    class NoBlockDefSymStart(sym: Symbol) extends DefSymStart(sym) {
      @deprecated("0.4.0", "DEF(x) := BLOCK() is no longer supported. Use PROC(x) or DEFINFER(x).")
      def :=(rhs: Block): DefDef = sys.error("DEF(x) := BLOCK() is no longer supported. Use PROC(x) or DEFINFER(x).")
      override def :=(rhs: Tree): DefDef = mkTree(rhs)
    }

    trait TreeDefStart[ResultTreeType <: Tree, RhsTreeType <: Tree] extends DefStart[ResultTreeType, RhsTreeType] {
      def defaultMods = NoMods
      def defaultPos  = NoPosition
      def :=(rhs: RhsTreeType): ResultTreeType = mkTree(rhs)
    }

    trait TreeVODDStart[ResultTreeType <: Tree] extends VODDStart[ResultTreeType, Tree] with TreeDefStart[ResultTreeType, Tree] {
      def defaultTpt  = TypeTree()
    }

    class ValNameStart(val name: Name) extends TreeVODDStart[ValDef] with ValCreator {
    }
    class ValTreeStart(val lhs: Tree) extends TreeVODDStart[ValDef] with ValCreator {
      val name = newTermName("")
      
      override def mkTree(rhs: Tree): ValDef = ValDef(mods, lhs, rhs)
    }
    
    class DefTreeStart(val name: Name) extends TreeVODDStart[DefDef] with DefCreator with TparamsStart with VparamssStart

    class ProcTreeStart(val name: Name) extends TreeDefStart[ProcDef, Block] with ProcCreator with TparamsStart with VparamssStart
    
    class NoBlockDefTreeStart(name: Name) extends DefTreeStart(name) {
      @deprecated("0.4.0", "DEF(x) := BLOCK() is no longer supported. Use PROC(x) or DEFINFER(x).")
      def :=(rhs: Block): DefDef = sys.error("DEF(x) := BLOCK() is no longer supported. Use PROC(x) or DEFINFER(x).")
      override def :=(rhs: Tree): DefDef = mkTree(rhs)
    }

    class AnonFuncStart extends TreeDefStart[AnonFunc, Tree] with TptStart with VparamssStart {
      def name        = ""
      def defaultTpt  = TypeTree()
      
      def mkTree(rhs: Tree): AnonFunc = AnonFunc(vparamss, tpt, rhs)
      final def ==>(rhs: Tree) = mkTree(rhs)
    }

    case class IfStart(cond: Tree, thenp: Tree) {
      def THEN(x: Tree)     = IfStart(cond, x)
      def ELSE(elsep: Tree) = If(cond, thenp, elsep)
      def ENDIF             = If(cond, thenp, EmptyTree)

      def enumerator = ForFilter(cond)
    }
    case class TryStart(body: Tree, catches: List[CaseDef], fin: Tree) {
      def CATCH(xs: CaseDef*): TryStart = CATCH(xs.toList)
      def CATCH(xs: Iterable[CaseDef]): TryStart = TryStart(body, xs.toList, fin)
      
      def FINALLY(x: Tree)    = Try(body, catches, x)
      def ENDTRY              = Try(body, catches, fin)
    }
    case class ForStart(enums: List[Enumerator]) {
      def DO(body: Tree)    = ForTree(enums, body)
      def YIELD(body: Tree) = ForYieldTree(enums, body) 
    }
    case class WhileStart(cond: Tree) {
      def DO(body: Tree)    = LabelDef(nme.WHILEkw, cond, body)
    }
    def CASE(pat: Tree): CaseStart  = new CaseStart(pat, EmptyTree)
    def CASE(pat: Tree, ifs: IfStart): CaseStart = CaseStart(pat, ifs.cond)
    def DEFAULT: CaseStart          = new CaseStart(WILD.empty, EmptyTree)

    class SymbolMethods(target: Symbol) {
      // def BIND(body: Tree) = Bind(target, body)
      def IS_NULL()  = REF(target) OBJ_EQ NULL
      def NOT_NULL() = REF(target) OBJ_NE NULL

      def GET() = fn(REF(target), nme.get)

      // name of nth indexed argument to a method (first parameter list), defaults to 1st
      // def ARG(idx: Int = 0) = Ident(target.paramss.head(idx))
      // def ARGS = target.paramss.head
      // def ARGNAMES = ARGS map Ident
    }
    
    class ForValFromStart(val varDecl: Tree) {
      def <--(rhs: Tree): ForValFrom = ForValFrom(varDecl, rhs)
    }
    class ForValDefStart(val varDecl: Tree) {
      def :=(rhs: Tree): ForValDef = ForValDef(varDecl, rhs)
    }
    
    trait ParentsStart {
      private var _parents: List[Tree] = Nil
      private var _earlydefs: Option[Block] = None
      private var _selfDef: ValDef = emptyValDef

      def withParents(parent0: Type, parents: Type*): this.type = withParents(parent0 :: parents.toList)
      def withParents(parents: Iterable[Type]): this.type = {
        _parents = _parents ::: (parents.toList map {TypeTree(_)})
        this 
      }
      def withParents(trees: Tree*): this.type = {
        _parents = _parents ::: trees.toList
        this
      }

      def withEarlyDefs(trees: Tree*): this.type = withEarlyDefs(trees.toList)
      def withEarlyDefs(trees: Iterable[Tree]): this.type = {
        trees.toList match {
          case List(b: Block) => _earlydefs = Some(b)
          case _ => _earlydefs = Some(Block(trees.toList: _*))
        }
        this        
      }
      
      def withSelf(param: ValDef): this.type = {
        _selfDef = param
        this
      }
      def withSelf(name: Name, tps: Type*): this.type =
        withSelf(tps.toList match {
          case Nil => VAL(name)
          case List(tp) => VAL(name, tp)
          case tps => VAL(name, makeRefinedType(tps))
        })

      def withSelf(sym: Symbol, tps: Type*): this.type =
        withSelf(tps.toList match {
          case Nil => VAL(sym)
          case List(tp) => VAL(sym, tp)
          case tps => VAL(sym, makeRefinedType(tps))
        })

      final def parents: List[Tree] = _earlydefs.toList ::: _parents
      final def selfDef: ValDef = _selfDef
    }

    class ClassDefStart(val name: TypeName) extends TreeDefStart[ClassDef, Tree] with TparamsStart with ParentsStart {
      private var _vparams: List[ValDef] = Nil
      private var _ctormods: Modifiers = NoMods

      def withParams(param: ValDef*): this.type = withParams(param.toList)
      def withParams(param: Iterable[ValDef]): this.type = {
        _vparams = param.toList
        this
      }
      def withCtorFlags(flags: Long*): this.type = {
        _ctormods = flags.foldLeft(_ctormods)(_ | _)
        this
      }
      def withCtorFlags(pin: PRIVATEWITHIN): this.type = {
        _ctormods = _ctormods | Flags.PRIVATE
        _ctormods = Modifiers(_ctormods.flags, pin.name, _ctormods.annotations)
        this
      }

      def vparams: List[ValDef] = _vparams
      def ctormods: Modifiers = _ctormods
      
      def mkTree(rhs: Tree): ClassDef = rhs match {
        case Block(xs, x) => mkTree(xs ::: List(x))
        case EmptyTree => mkTree(Nil)
        case _ => mkTree(rhs :: Nil)
      }
      
      def mkTree(body: List[Tree]): ClassDef = ClassDef(mods, ctormods, name, tparams, vparams, Template(parents, selfDef, body))
    }
    
    class TraitDefStart(name: TypeName) extends ClassDefStart(name) {
      override def mkTree(body: List[Tree]): ClassDef =
        ClassDef(mods | Flags.TRAIT, ctormods, name, tparams, vparams, Template(parents, selfDef, body))
    }
    
    class ModuleDefStart(val name: TermName) extends TreeDefStart[ModuleDef, Tree] with ParentsStart {

      
      def mkTree(rhs: Tree): ModuleDef = rhs match {
        case Block(xs, x) => mkTree(xs ::: List(x))
        case EmptyTree => mkTree(Nil)
        case _ => mkTree(rhs :: Nil)
      }
      
      def mkTree(body: List[Tree]): ModuleDef = ModuleDef(mods, name, Template(parents, selfDef, body))
    }
    
    trait PackageCreator extends TreeDefStart[PackageDef, Tree] {
      def header: Boolean

      override def defaultMods =
        if (header) NoMods | HEADER
        else NoMods
      
      def mkTree(body: List[Tree]): PackageDef

      def mkTree(rhs: Tree): PackageDef = rhs match {
        case Block(xs, x) => mkTree(xs ::: List(x))
        case EmptyTree => mkTree(Nil)
        case _ => mkTree(rhs :: Nil)
      }
    }

    class PackageDefStart(val name: TermName, val header: Boolean) extends PackageCreator {  
      def mkTree(body: List[Tree]): PackageDef = PackageDef(mods, Ident(name), body)
    }

    class PackageSymStart(val sym: Symbol, val header: Boolean) extends PackageCreator {
      def name = sym.name
      def mkTree(body: List[Tree]): PackageDef =
        if (sym == NoSymbol) PackageDef(mods, NoPackage, body)
        else PackageDef(mods, Ident(sym), body) setSymbol sym
    }

    sealed trait TypeBoundsStart
    case class LowerTypeBoundsStart(lo: Type) extends TypeBoundsStart
    case class UpperTypeBoundsStart(hi: Type) extends TypeBoundsStart
    case class ViewBoundsStart(target: Type) extends TypeBoundsStart
    case class ContextBoundsStart(typcon: Type) extends TypeBoundsStart
    
    trait TypeDefStart extends TreeDefStart[TypeDef, Tree] with TparamsStart {
      private var _bounds: List[TypeBoundsStart] = Nil
      
      private def withBounds(bounds: TypeBoundsStart*): this.type = {
        _bounds = _bounds ::: bounds.toList
        this
      }

      def mkTree(typ: Type): TypeDef = mkTree(TypeTree(typ))
      
      def LOWER(lo: Type) = withBounds(LowerTypeBoundsStart(lo))
      def UPPER(hi: Type) = withBounds(UpperTypeBoundsStart(hi))
      def VIEWBOUNDS(target: Type) = withBounds(ViewBoundsStart(target))
      def CONTEXTBOUNDS(typcon: Type) = withBounds(ContextBoundsStart(typcon))

      def bounds: Tree =
        if (_bounds.isEmpty) EmptyTree
        else TypeTree(TypeBounds(
          (_bounds collect {
            case LowerTypeBoundsStart(lo) => lo
          } headOption) getOrElse(NothingClass.tpe),
          (_bounds collect {
            case UpperTypeBoundsStart(hi) => hi
          } headOption) getOrElse(NothingClass.tpe),
          (_bounds collect {
            case ViewBoundsStart(trg) => trg
          } headOption) getOrElse(NothingClass.tpe),
          (_bounds collect {
            case ContextBoundsStart(typcon) => typcon
          } headOption) getOrElse(NothingClass.tpe)
        ))
    }
    
    class TypeDefTreeStart(val name: Name) extends TypeDefStart {
      def mkTree(rhs: Tree): TypeDef =
        TypeDef(mods, name.toTypeName, tparams,
          if (rhs.isEmpty) bounds else rhs)
    }
    
    class TypeDefSymStart(val sym: Symbol) extends TypeDefStart {
      def name = sym.name.toTypeName
      
      def mkTree(rhs: Tree): TypeDef =
        TypeDef(mods, name, tparams,
          if (rhs.isEmpty) bounds else rhs) setSymbol sym
    }
    
    class ImportSelectorStart(val name: TermName) {
      def ==>(rename: String): ImportSelector = ImportSelector(name, -1, rename, -1)
    }

    class AnnotationInfoStart(val typ: Type, val args: List[Tree]) {
      def annotation: AnnotationInfo = AnnotationInfo(typ, args, Nil)
    }
    
    /** Top level accessible. */
    def MATCHERROR(arg: Tree) = Throw(New(TypeTree(MatchErrorClass.tpe), List(List(arg))))
    /** !!! should generalize null guard from match error here. */
    def THROW(typ: Type): Throw = Throw(New(TypeTree(typ), List(Nil)))
    def THROW(typ: Type, msg: String): Throw = Throw(New(TypeTree(typ), List(List(LIT(msg)))))
    def THROW(typ: Type, msg: Tree): Throw = Throw(New(TypeTree(typ), List(List(msg.TOSTRING))))
    def THROW(tree: Tree): Throw = Throw(tree)

    def NEW(tp: Type, args: Tree*): Tree = NEW(TypeTree(tp), args: _*)
    def NEW(tpt: Tree, args: Tree*): Tree   =
      if (args.toList.isEmpty) New(tpt)
      else New(tpt, List(args.toList))
    // def NEW(sym: Symbol, args: Tree*): Tree = New(sym, args: _*)

    def PROC(name: Name): ProcTreeStart             = new ProcTreeStart(name)
    def PROC(sym: Symbol): ProcSymStart             = new ProcSymStart(sym)

    def DEF(name: Name, tp: Type): DefTreeStart     = new DefTreeStart(name) withType tp
    def DEF(name: Name)                             = new NoBlockDefTreeStart(name)
    def DEF(sym: Symbol, tp: Type): DefSymStart     = new DefSymStart(sym) withType tp
    def DEF(sym: Symbol)                            = new NoBlockDefSymStart(sym)
    def DEFINFER(sym: Symbol): DefSymStart          = new DefSymStart(sym)
    def DEFINFER(name: Name): DefTreeStart          = new DefTreeStart(name)

    def DEFTHIS: DefTreeStart                       = new DefTreeStart(nme.THIS)

    def VAL(name: Name, tp: Type): ValNameStart     = VAL(name) withType tp
    def VAL(name: Name): ValNameStart               = new ValNameStart(name)
    def VAL(sym: Symbol, tp: Type): ValSymStart     = VAL(sym) withType tp
    def VAL(sym: Symbol): ValSymStart               = new ValSymStart(sym)
    def VAL(tree: Tree): ValTreeStart               = new ValTreeStart(tree)

    def VAR(name: Name, tp: Type): ValNameStart     = VAL(name, tp) withFlags Flags.MUTABLE
    def VAR(name: Name): ValNameStart               = VAL(name) withFlags Flags.MUTABLE
    def VAR(sym: Symbol, tp: Type): ValSymStart     = VAL(sym, tp) withFlags Flags.MUTABLE
    def VAR(sym: Symbol): ValSymStart               = VAL(sym) withFlags Flags.MUTABLE
    def VAR(tree: Tree): ValTreeStart               = VAL(tree) withFlags Flags.MUTABLE
    
    def PARAM(name: Name, tp: Type): ValNameStart   = VAL(name, tp) withFlags Flags.PARAM
    def PARAM(name: Name): ValNameStart             = VAL(name) withFlags Flags.PARAM
    def PARAM(sym: Symbol, tp: Type): ValSymStart   = VAL(sym, tp) withFlags Flags.PARAM
    def PARAM(sym: Symbol): ValSymStart             = VAL(sym) withFlags Flags.PARAM
    def PARAM(tree: Tree): ValTreeStart             = VAL(tree) withFlags Flags.PARAM

    def LAZYVAL(name: Name, tp: Type): ValNameStart = VAL(name, tp) withFlags Flags.LAZY
    def LAZYVAL(name: Name): ValNameStart           = VAL(name) withFlags Flags.LAZY
    def LAZYVAL(sym: Symbol, tp: Type): ValSymStart = VAL(sym, tp) withFlags Flags.LAZY
    def LAZYVAL(sym: Symbol): ValSymStart           = VAL(sym) withFlags Flags.LAZY

    def VALFROM(valDecl: Tree): ForValFromStart           = new ForValFromStart(valDecl)
    def VALDEF(valDecl: Tree): ForValDefStart           = new ForValDefStart(valDecl)

    def CLASSDEF(name: Name): ClassDefStart         = new ClassDefStart(name.toTypeName)
    def CLASSDEF(sym: Symbol): ClassDefStart        = new ClassDefStart(sym.name.toTypeName)
    
    def CASECLASSDEF(name: Name): ClassDefStart     = CLASSDEF(name) withFlags Flags.CASE
    def CASECLASSDEF(sym: Symbol): ClassDefStart    = CLASSDEF(sym) withFlags Flags.CASE
    
    def ANONDEF(parent0: Type, parents: Type*): ClassDefStart =
      ANONDEF(parent0 :: parents.toList)
    def ANONDEF(parents: Iterable[Type]): ClassDefStart =
      ANONDEF(parents.toList map {TypeTree(_)}: _*)
    def ANONDEF(trees: Tree*): ClassDefStart =
      CLASSDEF(tpnme.ANON_CLASS_NAME) withParents(trees: _*)
    
    def TRAITDEF(name: Name): ClassDefStart         = new TraitDefStart(name.toTypeName)
    def TRAITDEF(sym: Symbol): ClassDefStart        = new TraitDefStart(sym.name.toTypeName)

    def OBJECTDEF(name: Name): ModuleDefStart       = new ModuleDefStart(name)
    def OBJECTDEF(sym: Symbol): ModuleDefStart      = new ModuleDefStart(sym.name)
        
    def CASEOBJECTDEF(name: Name): ModuleDefStart   = OBJECTDEF(name) withFlags Flags.CASE
    def CASEOBJECTDEF(sym: Symbol): ModuleDefStart  = OBJECTDEF(sym) withFlags Flags.CASE

    def PACKAGEOBJECTDEF(name: Name): ModuleDefStart =
      OBJECTDEF(name) withFlags Flags.PACKAGE
    def PACKAGEOBJECTDEF(sym: Symbol): ModuleDefStart =
      OBJECTDEF(sym) withFlags Flags.PACKAGE
    
    def PACKAGE(name: Name): PackageDefStart        = new PackageDefStart(name, false)
    def PACKAGE(sym: Symbol): PackageSymStart       = new PackageSymStart(sym, false)
    def PACKAGEHEADER(name: Name): PackageDefStart  = new PackageDefStart(name, true)
    def PACKAGEHEADER(sym: Symbol): PackageSymStart = new PackageSymStart(sym, true)
    
    def TYPEVAR(name: Name): TypeDefTreeStart       = new TypeDefTreeStart(name)
    def TYPEVAR(sym: Symbol): TypeDefSymStart       = new TypeDefSymStart(sym)
    
    def LAMBDA(param: ValDef*): AnonFuncStart       = LAMBDA(param.toList)
    def LAMBDA(param: Iterable[ValDef]): AnonFuncStart = new AnonFuncStart() withParams(param)
    
    def RENAME(name: TermName): ImportSelectorStart = new ImportSelectorStart(name)
    
    def AND(guards: Tree*): Tree = AND(guards.toList)
    def AND(guards: Iterable[Tree]): Tree =
      if (guards.isEmpty) EmptyTree
      else guards reduceLeft mkInfixAnd

    def OR(guards: Tree*): Tree = OR(guards.toList)
    def OR(guards: Iterable[Tree]): Tree =
      if (guards.isEmpty) EmptyTree
      else guards reduceLeft mkInfixOr

    def IF(tree: Tree)    = new IfStart(tree, EmptyTree)
    
    def TRY(xs: Tree*): TryStart = TRY(xs.toList)
    def TRY(xs: Iterable[Tree]): TryStart =
      new TryStart(xs.toList match {
        case List(b: Block) => b
        case _ => Block(xs.toList: _*)
      }, Nil, EmptyTree)
    
    def FOR(xs: Enumerator*): ForStart = FOR(xs.toList) 
    def FOR(xs: Iterable[Enumerator]): ForStart = new ForStart(xs.toList)
    def WHILE(tree: Tree) = new WhileStart(tree)

    def INTERP(sym: Symbol, args: Tree*): Interpolated = Interpolated(sym, args.toList)
    def INTERP(name: Name, args: Tree*): Interpolated = Interpolated(name, args.toList)

    def BLOCK(xs: Iterable[Tree]): Block = Block(xs.toList: _*)
    def BLOCK(xs: Tree*): Block = Block(xs: _*)
    def NOT(tree: Tree)   = Select(tree, Boolean_not)

    def PLUS(tree: Tree)  = Select(tree, Int_plus)
    def MINUS(tree: Tree)  = Select(tree, Int_minus)
    def TILDE(tree: Tree)  = Select(tree, Int_tilde)
    
    def IMPORT(pck: Name, selectors: ImportSelector*): Import   = IMPORT(pck, selectors.toList)
    def IMPORT(pck: Name, selectors: Iterable[ImportSelector]): Import =
      Import(REF(definitions.getClass(pck)), selectors.toList)
    def IMPORT(sym: Symbol, selectors: ImportSelector*): Import = IMPORT(sym, selectors.toList)
    def IMPORT(sym: Symbol, selectors: Iterable[ImportSelector]): Import =
      Import(REF(sym), selectors.toList)
    def IMPORT(expr: Tree, selectors: ImportSelector*): Import  = IMPORT(expr, selectors.toList)
    def IMPORT(expr: Tree, selectors: Iterable[ImportSelector]): Import = Import(expr, selectors.toList)
    def SEQARG(tree: Tree) = Typed(tree, SEQ_WILDCARD)
    def RETURN(tree: Tree) = Return(tree)

    /** Typed trees from symbols. */
    def THIS(sym: Symbol)             = mkAttributedThis(sym)
    def THIS(name: Name)              = This(name.toTypeName)
    def THIS                          = This(EmptyTypeName)
    
    val SUPER                         = SuperStart(Super(EmptyTree))
    def SUPER(sym: Symbol)            = SuperStart(Super(THIS(sym)))
    def SUPER(name: Name)             = SuperStart(Super(THIS(name)))
    
    def ID(sym: Symbol)               = mkAttributedIdent(sym)
    def ID(name: Name)                = Ident(name)
    def BACKQUOTED(sym: Symbol)       = BackQuotedIdent(sym)
    def BACKQUOTED(name: Name)        = BackQuotedIdent(name)
    def REF(sym: Symbol)              = mkAttributedIdent(sym)
    def REF(pre: Type, sym: Symbol)   = mkAttributedRef(pre, sym)
    def REF(name: Name)               = Ident(name)

    def COVARIANT(name: Name): Name   = newTypeName("+" + name.name)
    def COVARIANT(symbol: Symbol): Symbol =
      symbol.owner.newAliasType(symbol.name).setFlag(Flags.COVARIANT)
    def CONTRAVARIANT(name: Name): Name   = newTypeName("-" + name.name)
    def CONTRAVARIANT(symbol: Symbol): Symbol =
      symbol.owner.newAliasType(symbol.name).setFlag(Flags.CONTRAVARIANT)
    
    case class PRIVATEWITHIN(name: Name)
    
    def PAREN(trees: Tree*): Tree = TUPLE(trees.toList)
    def PAREN(trees: Iterable[Tree]): Tree = TUPLE(trees)

    def TUPLE(trees: Tree*): Tree = TUPLE(trees.toList)
    def TUPLE(trees: Iterable[Tree], flattenUnary: Boolean = false): Tree = trees.toList match {
      case Nil                        => UNIT
      case List(tree) if flattenUnary => tree
      case _                          => mkTuple(trees.toList) // Apply(TupleClass(trees.length).companionModule, trees: _*)
    }
    
    def makeTupleType(trees: List[Tree], flattenUnary: Boolean = false): Tree = trees match {
      case Nil                        => scalaUnitConstr
      case List(tree) if flattenUnary => tree
      case _                          => AppliedTypeTree(REF(TupleClass(trees.length)), trees)
    }

    def ANNOT(typ: Type, args: Tree*): AnnotationInfo = ANNOT(typ, args.toList)
    def ANNOT(typ: Type, args: Iterable[Tree]): AnnotationInfo =
      AnnotationInfo(typ, args.toList, Nil)

    def LIST(xs: Tree*): Tree             = LIST(xs.toList)
    def LIST(xs: Iterable[Tree]): Tree    = ID("List") APPLY xs
    def SOME(xs: Tree*): Tree             = SOME(xs.toList)
    def SOME(xs: Iterable[Tree]): Tree    = Apply(SomeModule, TUPLE(xs, true))
    def RIGHT(tree: Tree): Tree           = RightClass.module APPLY tree
    def LEFT(tree: Tree): Tree            = LeftClass.module APPLY tree
    def ARRAY(xs: Tree*): Tree            = ARRAY(xs.toList)
    def ARRAY(xs: Iterable[Tree]): Tree   = ArrayClass.module APPLY xs
    def SEQ(xs: Tree*): Tree              = SEQ(xs.toList)
    def SEQ(xs: Iterable[Tree]): Tree     = SeqClass.module APPLY xs
    def VECTOR(xs: Tree*): Tree           = VECTOR(xs.toList)
    def VECTOR(xs: Iterable[Tree]): Tree  = VectorClass.module APPLY xs
    def MAKE_MAP(xs: Tree*): Tree         = MAKE_MAP(xs.toList)
    def MAKE_MAP(xs: Iterable[Tree]): Tree = ID("Map") APPLY xs

    def TYPE_*(typ: Type): Type       = repeatedParamType(typ)
    def TYPE_BYNAME(typ: Type): Type  = byNameParamType(typ)
    def TYPE_STRUCT(tree: Tree*): Type = TYPE_STRUCT(tree.toList)
    def TYPE_STRUCT(tree: Iterable[Tree]): Type = 
      tree.toList match {
        case List(Block(xs, x)) => makeStructuralType(xs ::: List(x))
        case _ => makeStructuralType(tree.toList)
      }
    def makeStructuralType(trees: List[Tree]): Type = {
      val customString = trees map { tree => treeToString(tree) } mkString("({ ", ", ", " })")
      refinedType(Nil, NoSymbol, trees, customString)
    }
    def makeRefinedType(args: List[Type]): Type =
      refinedType(args, NoSymbol, Nil, "")
    def TYPE_REF(sym: Symbol): Type   = typeRef(sym)
    def TYPE_REF(name: Name): Type    = TYPE_REF(RootClass.newClass(name))
    def TYPE_REF(tree: Tree): Type    = makePathType(tree)   
    def makePathType(tree: Tree): Type = {
      val customString = treeToString(tree)
      PathType(tree, customString)      
    }

    def TYPE_TUPLE(typs: Type*): Type = tupleType(typs.toList)
    def TYPE_TUPLE(typs: Iterable[Type]): Type = tupleType(typs.toList)
    def TYPE_ARRAY(typ: Type): Type   = ArrayClass TYPE_OF typ
    def TYPE_LIST(typ: Type): Type    = ListClass TYPE_OF typ
    def TYPE_SEQ(typ: Type): Type     = SeqClass TYPE_OF typ
    def TYPE_VECTOR(typ: Type): Type  = VectorClass TYPE_OF typ
    def TYPE_ITERATOR(typ: Type): Type = IteratorClass TYPE_OF typ
    def TYPE_MAP(k: Type, v: Type): Type = immutableMapType(k, v)
    def TYPE_SET(typ: Type): Type     = immutableSetType(typ)
    def TYPE_OPTION(typ: Type): Type  = optionType(typ)
    def TYPE_SOME(typ: Type): Type    = someType(typ)
    def TYPE_EITHER(arg1: Type, arg2: Type): Type = eitherType(arg1, arg2)
    def TYPE_RIGHT(arg1: Type, arg2: Type): Type = rightType(arg1, arg2)
    def TYPE_LEFT(arg1: Type, arg2: Type): Type = leftType(arg1, arg2)
    def TYPE_ORDERED(typ: Type): Type = orderedType(typ)
    def TYPE_=:=(arg1: Type, arg2: Type) = tpEqualsType(arg1, arg2)
    def TYPE_<:<(arg1: Type, arg2: Type) = conformsType(arg1, arg2)
    def TYPE_<%<(arg1: Type, arg2: Type) = conformsOrViewAsType(arg1, arg2)
    def TYPE_FUNCTION(args: Iterable[Type], result: Type) = functionType(args.toList, result)
    def TYPE_FUNCTION(typs: Type*): Type = TYPE_FUNCTION(typs.toList)
    def TYPE_FUNCTION(typs: Iterable[Type]): Type =
      typs.toList match {
        case Nil => sys.error("TYPE_FUNCTION must take at least one Type.")
        case x => functionType(x.init, x.last)
      }
    def TYPE_SINGLETON(tree: Tree) = singleType(TYPE_REF(tree), NoSymbol)

    // implicit def mkTermName(s: String): TermName = newTermName(s)


    /** Implicits - some of these should probably disappear **/
    implicit def mkTypeMethods(target: Type): TypeMethods = new TypeMethods(target)
    implicit def mkTypeMethodsFromSymbol(sym: Symbol): TypeMethods = new TypeMethods(sym.toType)

    implicit def mkTreeMethods(target: Tree): TreeMethods = new TreeMethods(target)
    implicit def mkTreeMethodsFromSymbol(target: Symbol): TreeMethods = new TreeMethods(Ident(target))
    implicit def mkTreeMethodsFromType(target: Type): TreeMethods = new TreeMethods(TypeTree(target))
    implicit def mkTreeMethodsFromSuperStart(target: SuperStart): TreeMethods = new TreeMethods(target.tree)
    implicit def mkSymbolMethodsFromSymbol(target: Symbol): SymbolMethods = new SymbolMethods(target)

    implicit def mkTreeFromTypeDefStart(tds: TypeDefStart): TypeDef = tds.tree
    implicit def mkSeqTypeDefFromCandidates[A <: TypeDefStart, M[A] <: Iterable[A]](in: M[A]): Seq[TypeDef] =
      in.toSeq map { x => (x: TypeDef) }
    
    implicit def mkTreeFromType(typ: Type): TypeTree =TypeTree(typ)
    implicit def mkSeqTypeTreeFromCandidates[M[A] <: Iterable[A]](in: M[Type]): Seq[TypeTree] =
      in.toSeq map { x => (x: TypeTree) }
    
    implicit def mkSeqTreeFromCandidates[A <% Tree, M[A] <: Iterable[A]](in: M[A]): Seq[Tree] =
      in.toSeq map { x: A => (x: Tree)}
    
    /** (foo DOT bar) might be simply a Select, but more likely it is to be immediately
     *  followed by an Apply.  We don't want to add an actual apply method to arbitrary
     *  trees, so SelectStart is created with an apply - and if apply is not the next
     *  thing called, the implicit from SelectStart -> Tree will provide the tree.
     */
    implicit def mkTreeFromSelectStart(ss: SelectStart): Select = ss.tree
    implicit def mkSeqTreeFromSelectStarts[M[A] <: Iterable[A]](in: M[SelectStart]): Seq[Select] =
      in.toSeq map { x => (x: Select)}
    
    /** (SUPER) might be simply a Super.
     */
    implicit def mkTreeFromSuperStart(ss: SuperStart): Super = ss.tree
    implicit def mkSeqTreeFromSuperStarts[M[A] <: Iterable[A]](in: M[SuperStart]): Seq[Super] =
      in.toSeq map { x => (x: Super)}

    implicit def mkTreeMethodsFromSelectStart(ss: SelectStart): TreeMethods = mkTreeMethods(ss.tree)
    
    implicit def mkTreeFromDefStart[A <: Tree, B <: Tree](start: DefStart[A, B]): A = start.empty
    implicit def mkSeqTreeFromDefStarts[A <: Tree, M[A] <: Iterable[A], B <: Tree](in: M[DefStart[A, B]]): Seq[A] =
      in.toSeq map { x: DefStart[A, B] => (x: A)}

    implicit def mkTypeFromSymbol(sym: Symbol): Type = TYPE_REF(sym)
    implicit def mkTypeFromString(str: String): Type = TYPE_REF(RootClass.newClass(str))
    implicit def mkSeqTypeFromCandidates[A <% Type, M[A] <: Iterable[A]](in: M[A]): Seq[Type] =
      in.toSeq map { x: A => (x: Type)}
    
    implicit def mkImportSelectorFromString(name: String): ImportSelector = ImportSelector(name, -1, name, -1)
    implicit def mkSeqImportSelectorFromCandidates[A <% ImportSelector, M[A] <: Iterable[A]](in: M[A]): Seq[ImportSelector] =
      in.toSeq map { x: A => (x: ImportSelector)}
    
    implicit def mkEnumeratorFromIfStart(ifs: IfStart): Enumerator = ifs.enumerator
    implicit def mkDocElementFromString(str: String): DocElement = DocText(str)
  }
}
