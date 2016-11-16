/** This file implements valid transformations on
  * a different numbers of Nodes
  */
package chiselutils.algorithms

import collection.mutable.ArrayBuffer

object Transforms {

  /** Combine two uks into 1 and return the index mapping to them
    */
  def commonMapping( uKa : List[Set[Vector[Int]]], uKb : List[Set[Vector[Int]]]) :
      ( List[Set[Vector[Int]]], List[Int], List[Int] ) = {
    var aIdx = 0
    var bIdx = 0
    val aIdxMap = ArrayBuffer[Int]()
    val bIdxMap = ArrayBuffer[Int]()
    val uKc = ArrayBuffer[Set[Vector[Int]]]()
    while ( aIdx < uKa.size || bIdx < uKb.size ) {
      if ( bIdx == uKb.size || ( aIdx != uKa.size && uKa(aIdx).hashCode < uKb(bIdx).hashCode ) ) {
        aIdxMap += uKc.size
        uKc += uKa( aIdx )
        aIdx += 1
      } else {
        bIdxMap += uKc.size
        uKc += uKb( bIdx )
        bIdx += 1
      }
    }
    ( uKc.toList, aIdxMap.toList, bIdxMap.toList )
  }

  /** Look at two nodes and try to merge them
    * return new node if merge was done
    * seach parents for common l/r
    * check overlap and that constraint type the same
    * create new merged node
    * look if all L or all R for mux. if so set l/r
    */
  def tryMerge( nA : Node , nB : Node ) : Option[Node] = {
    // check number of distinct inputs
    val sameIn = nA.getL() == nB.getL() && nA.getR() == nB.getR()
    val oppositeIn = nA.getL() == nB.getR() && nA.getR() == nB.getL()
    if ( sameIn || oppositeIn ) {
      val mapping = commonMapping( nA.getUk(), nB.getUk() )
      val ck = ( nA.getCk() zip nB.getCk() ).map( cki => {
        if ( cki._1 == -1 && cki._2 == -1 )
          -1
        else if ( cki._2 == -1 )
          mapping._2( cki._1 )
        else if ( cki._1 == -1 )
          mapping._3( cki._2 )
        else {
          if ( mapping._2( cki._1 ) != mapping._3( cki._2 ) )
            return None // as cannot merge under this condition
          mapping._2( cki._1 )
        }
      })
      // create the new node
      val nC = Node( mapping._1, ck )

      // set l/r and parents
      if ( sameIn ) {
        if ( nA.getL().isDefined )
          nC.setL( nA.getL().get )
        if ( nA.getR().isDefined )
          nC.setR( nA.getR().get )
      } else {
        if ( nA.getR().isDefined )
          nC.setL( nA.getR().get )
        if ( nA.getL().isDefined )
          nC.setR( nA.getL().get )
      }

      // fix parent links
      for ( p <- nA.getParents() ++ nB.getParents() ) {
        if ( p.getL().isDefined && ( p.getL().get == nA || p.getL().get == nB ) )
          p.setL( nC )
        if ( p.getR().isDefined && ( p.getR().get == nA || p.getR().get == nB ) )
          p.setR( nC )
      }

      return Some( nC )
    }

    None
  }

  private def incr( uk : List[Set[Vector[Int]]] ) : List[Set[Vector[Int]]] = {
    uk.map( s => s.map( v => { Vector( v(0) + 1 ) ++ v.drop(1) }))
  }

  private def rotateDown( ck : List[Int] ) : List[Int] = {
    ck.takeRight( 1 ) ++ ck.dropRight( 1 )
  }

  private def combineAdd( uk1 : List[Set[Vector[Int]]], ck1 : List[Int],
    uk2 : List[Set[Vector[Int]]], ck2 : List[Int] ) : ( List[Set[Vector[Int]]], List[Int] ) = {
    val ckCombined = ck1.zip( ck2 )
    val uKidx = ckCombined.distinct.filter( _ != ( -1, -1 ) )
    val ckNew = ckCombined.map( uKidx.indexOf( _ ) )
    // should never have one as -1 and not the other
    val ukNew = uKidx.map( uki => uk1( uki._1 ) ++ uk2( uki._2 ) )
    ( ukNew, ckNew )
  }

  private def combineMux( uk1 : List[Set[Vector[Int]]], ck1 : List[Int],
    uk2 : List[Set[Vector[Int]]], ck2 : List[Int] ) : ( List[Set[Vector[Int]]], List[Int] ) = {
    val ukNew = (uk1 ++ uk2).distinct
    val uk1Idx = uk1.map( uki => ukNew.indexOf(uki) )
    val uk2Idx = uk2.map( uki => ukNew.indexOf(uki) )
    val ckNew = ck1.zip( ck2 ).map( cks => {
      if ( cks._1 == -1 && cks._2 == -1 )
        -1
      else if ( cks._1 == -1 )
        uk2Idx( cks._2 )
      else if ( cks._2 == -1 )
        uk1Idx( cks._1 )
      else {
        assert( uk1Idx( cks._1 ) == uk2Idx( cks._2 ), "Invalid mux combine" )
        uk1Idx( cks._1 )
      }
    })
    ( ukNew, ckNew )
  }

  private def filterCk( ck : List[Int], ckFilter : List[Int] ) : List[Int] = {
    ckFilter.zip( ck ).map( cks => if ( cks._1 == -1 ) -1 else cks._2 )
  }

  /** nSwap satisfies constraintA, nPar satisfies constraintB
    * nPar.lNode = nSwap
    * nPar.rNode = nSwap
    * transformed to (6):
    * nPar.lNode = nodeA
    * nPar.rNode = nodeB
    * nodeA.lNode = nSwap.lNode
    * nodeA.rNode = nSwap.lNode
    * nodeB.lNode = nSwap.rNode
    * nodeB.rNode = nSwap.rNode
    */
  private def swapCase1( nPar : Node, nSwap : Node ) : List[Node] = {
    val nodeAuK = nSwap.getL().get.getUkNext()
    val nodeAcK = nSwap.getL().get.getCkNext()
    val nodeAcKFiltered = nodeAcK.zip( nSwap.getCk() ).map( z => if ( z._2 == -1 ) -1 else z._1 )

    val nodeA = Node( nodeAuK, nodeAcKFiltered )
    nodeA.setL( nSwap.getL().get )
    nodeA.setR( nSwap.getL().get )
    nodeA.setB()
    val nodeBuK = nSwap.getR().get.getUkNext()
    val nodeBcK = nSwap.getR().get.getCkNext()
    val nodeBcKFiltered = nodeBcK.zip( nSwap.getCk() ).map( z => if ( z._2 == -1 ) -1 else z._1 )

    val nodeB = Node( nodeBuK, nodeBcKFiltered )
    nodeB.setL( nSwap.getR().get )
    nodeB.setR( nSwap.getR().get )
    nodeB.setB()

    nPar.setL( nodeA )
    nPar.setR( nodeB )
    nPar.setA()
    List( nPar, nodeA, nodeB )
  }

  /** nSwap satisfies constraintB, nPar satisfies constraintB
    * nPar.lNode = nSwap
    * nPar.rNode = nSwap
    * transformed to (10):
    * nPar.lNode = nodeA
    * nPar.rNode = nodeB
    * nodeA.lNode = nSwap.lNode
    * nodeA.rNode = nSwap.lNode
    * nodeB.lNode = nSwap.rNode
    * nodeB.rNode = nSwap.rNode
    */
  private def swapCase2( nPar : Node, nSwap : Node ) : List[Node] = {
    val nodeList = swapCase1( nPar, nSwap ) // same as nPar holds the add/mux info
    nPar.setB()
    nodeList
  }

  /** nSwap satisfies constraintB, nPar satisfies constraintA, nOther satisfies constraintA
    * nPar.lNode = nSwap
    * nPar.rNode = nOther
    * nSwap.lNode = nSwap.rNode
    * transformed to (10):
    * nPar.lNode = nodeA
    * nPar.rNode = nodeB
    * nodeA.lNode = nSwap.lNode
    * nodeA.rNode = nOther.lNode
    * nodeB.lNode = nOther.rNode
    * nodeB.rNode = nOther.rNode
    */
  private def swapCase4( nPar : Node, nSwap : Node, nOther : Node ) : List[Node] = {
    // nSwap must be the reg by itself
    // rotate so that nOther.rNode + nSwap.lNode and nOther.lNode is reg

    val otherLcK = filterCk( nOther.getL().get.getCkNext(), nOther.getCk() )
    val otherLuK = nOther.getL().get.getUkNext()
    val nodeA = Node( otherLuK, otherLcK )
    nodeA.setL( nOther.getL().get )
    nodeA.setR( nOther.getL().get )
    nodeA.setB()

    // find distinct ck combinations
    val otherRcK = filterCk( nOther.getR().get.getCkNext(), nOther.getCk() )
    val otherRuK = nOther.getR().get.getUkNext()
    val swapuK = nSwap.getUk()
    val swapcK = nSwap.getCk()

    // combine two as union
    val combAdd = combineAdd( swapuK, swapcK, otherRuK, otherRcK )
    val nodeB = Node( combAdd._1, combAdd._2 )
    nodeB.setA()
    nodeB.setL( nOther.getR().get )
    nodeB.setR( nSwap.getL().get )

    nPar.setL( nodeA )
    nPar.setR( nodeB )
    nPar.setA()
    List( nPar, nodeA, nodeB )
  }

  /** nSwap satisfies constraintA, nPar satisfies constraintA, nOther satisfies constraintA
    * nPar.lNode = nSwap
    * nPar.rNode = nOther
    * transformed to (5):
    * nPar.lNode = nodeA
    * nPar.rNode = nodeB
    * nodeA.lNode = nOther.lNode
    * nodeA.rNode = nSwap.lNode
    * nodeB.lNode = nSwap.rNode
    * nodeB.rNode = nOther.rNode
    */
  private def swapCase5( nPar : Node, nSwap : Node, nOther : Node ) : List[Node] = {
    val otherLFiltered = filterCk( nOther.getL().get.getCkNext(), nOther.getCk() )
    val otherRFiltered = filterCk( nOther.getR().get.getCkNext(), nOther.getCk() )
    val swapLFiltered = filterCk( nSwap.getL().get.getCkNext(), nSwap.getCk() )
    val swapRFiltered = filterCk( nSwap.getR().get.getCkNext(), nSwap.getCk() )
    val nodeAComb = combineAdd( nOther.getL().get.getUkNext(), otherLFiltered,
      nSwap.getL().get.getUkNext(), swapLFiltered )
    val nodeBComb = combineAdd( nOther.getR().get.getUkNext(), otherRFiltered,
      nSwap.getR().get.getUkNext(), swapRFiltered )

    val nodeA = Node( nodeAComb._1, nodeAComb._2 )
    val nodeB = Node( nodeBComb._1, nodeBComb._2 )
    nodeA.setA()
    nodeA.setL( nOther.getL().get )
    nodeA.setR( nSwap.getL().get )
    nodeB.setL( nSwap.getR().get )
    nodeB.setR( nOther.getR().get )
    nodeB.setA()

    nPar.setL( nodeA )
    nPar.setR( nodeB )
    nPar.setA()
    List( nPar, nodeA, nodeB )
  }

  /** nSwap satisfies constraintB, nPar satisfies constraintA, nOther satisfies constraintB
    * nPar.lNode = nSwap
    * nPar.rNode = nOther
    * nSwap.lNode = nSwap.rNode
    * nOther.lNode = nOther.rNode
    * transformed to (1):
    * nPar.lNode = nodeA
    * nPar.rNode = nodeA
    * nodeA.lNode = nSwap.lNode
    * nodeA.rNode = nOther.lNode
    */
  private def swapCase6( nPar : Node, nSwap : Node, nOther : Node ) : List[Node] = {

    val othercK = filterCk( nOther.getL().get.getCkNext(), nOther.getCk() )
    val otheruK = nOther.getL().get.getUkNext()
    val swapcK = filterCk( nSwap.getL().get.getCkNext(), nSwap.getCk() )
    val swapuK = nSwap.getL().get.getUkNext()
    val combAdd = combineAdd( otheruK, othercK, swapuK, swapcK )
    val nodeA = Node( combAdd._1, combAdd._2 )
    nodeA.setA()
    nodeA.setL( nSwap.getL().get )
    nodeA.setR( nOther.getL().get )

    nPar.setL( nodeA )
    nPar.setR( nodeA )
    nPar.setB()
    List( nPar, nodeA )
  }

  /** nSwap satisfies constraintB, nPar satisfies constraintA, nOther satisfies constraintB
    * nPar.lNode = nSwap
    * nPar.rNode = nOther
    * nSwap.lNode = nSwap.rNode
    * transformed to (11):
    * nPar.lNode = nodeA
    * nPar.rNode = nodeB
    * nodeA.lNode = nSwap.lNode
    * nodeA.rNode = nOther.lNode
    * nodeB.lNode = nSwap.lNode
    * nodeB.rNode = nOther.rNode
    */
  private def swapCase7( nPar : Node, nSwap : Node, nOther : Node ) : List[Node] = {
    val otherLcK = filterCk( nOther.getL().get.getCkNext(), nOther.getCk() )
    val otherLuK = nOther.getL().get.getUkNext()
    val otherRcK = filterCk( nOther.getR().get.getCkNext(), nOther.getCk() )
    val otherRuK = nOther.getR().get.getUkNext()
    val swapcK = nSwap.getCk()
    val swapuK = nSwap.getUk()
    val combAddL = combineAdd( otherLuK, otherLcK, swapuK, swapcK )
    val combAddR = combineAdd( otherRuK, otherRcK, swapuK, swapcK )
    val nodeA = Node( combAddL._1, combAddL._2 )
    nodeA.setL( nOther.getL().get )
    nodeA.setR( nSwap.getL().get )
    nodeA.setA()
    val nodeB = Node( combAddR._1, combAddR._2 )
    nodeB.setL( nSwap.getL().get )
    nodeB.setR( nOther.getR().get )
    nodeB.setA()
    nPar.setL( nodeA )
    nPar.setR( nodeB )
    nPar.setB()
    List( nPar, nodeA, nodeB )
  }

  /** nSwap satisfies constraintB, nPar satisfies constraintA, nOther satisfies constraintB
    * nPar.lNode = nSwap
    * nPar.rNode = nOther
    * nSwap.lNode = nSwap.rNode
    * nOther.lNode = nOther.rNode
    * transformed to (2):
    * nPar.lNode = nodeA
    * nPar.rNode = nodeA
    * nodeA.lNode = nSwap.lNode
    * nodeA.rNode = nOther.lNode
    */
  private def swapCase10( nPar : Node, nSwap : Node, nOther : Node ) : List[Node] = {
    val othercK = filterCk( nOther.getL().get.getCkNext(), nOther.getCk() )
    val otheruK = nOther.getL().get.getUkNext()
    val swapcK = filterCk( nSwap.getL().get.getCkNext(), nSwap.getCk() )
    val swapuK = nSwap.getL().get.getUkNext()
    val combMux = combineMux( otheruK, othercK, swapuK, swapcK )
    val nodeA = Node( combMux._1, combMux._2 )
    nodeA.setL( nSwap.getL().get )
    nodeA.setR( nOther.getL().get )
    nodeA.setB()
    nPar.setL( nodeA )
    nPar.setR( nodeA )
    nPar.setB()
    List( nPar, nodeA )
  }

  /** nSwap satisfies constraintA, nPar satisfies constraintB, nOther satisfies constraintA
    * nPar.lNode = nSwap
    * nPar.rNode = nOther
    * iff nSwap and nOther have lNode or rNode in common
    * transformed to (7):
    * nPar.lNode = nodeA
    * nPar.rNode = nodeB
    * nodeA.lNode = nSwap.lNode
    * nodeA.rNode = nSwap.lNode
    * nodeB.lNode = nOther.lNode
    * nodeB.lNode = nOther.rNode
    */
  private def swapCase11( nPar : Node, nSwap : Node, nOther : Node ) : List[Node] = {
    // find common
    val swapLOtherL = nSwap.getL().get == nOther.getL().get
    val swapLOtherR = nSwap.getL().get == nOther.getR().get
    val swapROtherL = nSwap.getR().get == nOther.getL().get
    val swapROtherR = nSwap.getR().get == nOther.getR().get
    val swapL = swapLOtherL || swapLOtherR
    val swapR = swapROtherL || swapROtherR
    val otherL = swapLOtherL || swapROtherL
    if ( swapL || swapR ) {
      val commonNode = { if ( swapL ) nSwap.getL().get else nSwap.getR().get }
      val swapSpare = { if ( swapL ) nSwap.getR().get else nSwap.getL().get }
      val otherSpare = { if ( otherL ) nOther.getR().get else nOther.getL().get }

      val nodeAuK = commonNode.getUkNext()
      val nodeAcKUp = commonNode.getCkNext()
      val ckComb = nSwap.getCk().zip( nOther.getCk() ).map( cks => {
        if ( cks._1 == -1 )
          cks._2
        else {
          assert( cks._2 == -1, "Only one should be empty as followed my mux" )
          cks._1
        }
      })
      val nodeAcK = filterCk( nodeAcKUp, ckComb )
      val nodeA = Node( nodeAuK, nodeAcK )
      nodeA.setB()
      nodeA.setL( commonNode )
      nodeA.setR( commonNode )

      val swapCkFiltered = filterCk( swapSpare.getCkNext(), nSwap.getCk() )
      val otherCkFiltered = filterCk( otherSpare.getCkNext(), nOther.getCk() )
      val combMux = combineMux( swapSpare.getUkNext(), swapCkFiltered,
        otherSpare.getUkNext(), otherCkFiltered )
      val nodeB = Node( combMux._1, combMux._2 )
      nodeB.setB()
      nodeB.setL( swapSpare )
      nodeB.setR( otherSpare )

      nPar.setL( nodeA )
      nPar.setR( nodeB )
      nPar.setA()
      return List( nPar, nodeA, nodeB )
    }
    List[Node]()
  }

  /** nSwap satisfies constraintB, nPar satisfies constraintB, nOther satisfies constraintB
    * nPar.lNode = nSwap
    * nPar.rNode = nOther
    * transformed to (12):
    * nPar.lNode = nodeA
    * nPar.rNode = nodeB
    * nodeA.lNode = nOther.lNode
    * nodeA.rNode = nSwap.lNode
    * nodeB.lNode = nSwap.rNode
    * nodeB.rNode = nOther.rNode
    */
  private def swapCase12( nPar : Node, nSwap : Node, nOther : Node ) : List[Node] = {
    val otherLFiltered = filterCk( nOther.getL().get.getCkNext(), nOther.getCk() )
    val otherRFiltered = filterCk( nOther.getR().get.getCkNext(), nOther.getCk() )
    val swapLFiltered = filterCk( nSwap.getL().get.getCkNext(), nSwap.getCk() )
    val swapRFiltered = filterCk( nSwap.getR().get.getCkNext(), nSwap.getCk() )
    val nodeAComb = combineMux( nOther.getL().get.getUkNext(), otherLFiltered,
      nSwap.getL().get.getUkNext(), swapLFiltered )
    val nodeBComb = combineMux( nOther.getR().get.getUkNext(), otherRFiltered,
      nSwap.getR().get.getUkNext(), swapRFiltered )

    val nodeA = Node( nodeAComb._1, nodeAComb._2 )
    val nodeB = Node( nodeBComb._1, nodeBComb._2 )
    nodeA.setB()
    nodeA.setL( nOther.getL().get )
    nodeA.setR( nSwap.getL().get )
    nodeB.setL( nSwap.getR().get )
    nodeB.setR( nOther.getR().get )
    nodeB.setB()
    nPar.setL( nodeA )
    nPar.setR( nodeB )
    nPar.setB()
    List( nPar, nodeA, nodeB )
  }

  /** nSwap satisfies constraintB, nPar satisfies constraintB, nOther satisfies constraintB
    * nPar.lNode = nSwap
    * nPar.rNode = nOther
    * transformed to (13):
    * nPar.lNode = nodeA
    * nPar.rNode = nodeB
    * nodeA.lNode = nOther.lNode
    * nodeA.rNode = nOther.lNode
    * nodeB.lNode = nOther.rNode
    * nodeB.rNode = nSwap.lNode
    */
  private def swapCase13( nPar : Node, nSwap : Node, nOther : Node ) : List[Node] = {
    val otherLcK = filterCk( nOther.getL().get.getCkNext(), nOther.getCk() )
    val otherLuK = nOther.getL().get.getUkNext()
    val nodeA = Node( otherLuK, otherLcK )
    nodeA.setL( nOther.getL().get )
    nodeA.setR( nOther.getL().get )
    nodeA.setB()
    // find distinct ck combinations
    val otherRcK = filterCk( nOther.getR().get.getCkNext(), nOther.getCk() )
    val otherRuK = nOther.getR().get.getUkNext()
    val swapuK = nSwap.getUk()
    val swapcK = nSwap.getCk()

    // combine two as union
    val combMux = combineMux( swapuK, swapcK, otherRuK, otherRcK )
    val nodeB = Node( combMux._1, combMux._2 )
    nodeB.setB()
    nodeB.setL( nOther.getR().get )
    nodeB.setR( nSwap.getL().get )

    nPar.setL( nodeA )
    nPar.setR( nodeB )
    nPar.setB()
    List( nPar, nodeA, nodeB )
  }

  /** Look at two nodes and try to swap them
    */
  def trySwap( nPar : Node, nSwap : Node ) : List[Node] = {

    assert( nSwap.hasParent( nPar ), "For swap must have swap and parent node" )

    if ( nSwap.isC() )
      return List[Node]()

    // work out how nodes are connected ( should be directly )
    if ( nPar.isB() && nPar.getL() == nPar.getR() ) {
      if ( nSwap.isA() )
        return swapCase1( nPar, nSwap )
      if ( nSwap.isB() && nSwap.getL() != nSwap.getR() )
        return swapCase2( nPar, nSwap )
      return List[Node]() // case 3 which no point as changes nothing
    }
    val nOther = { if ( nPar.getL().get == nSwap ) nPar.getR().get else nPar.getL().get }

    if ( nPar.isA() ) {
      if ( nSwap.isB() && nSwap.getL() == nSwap.getR() ) {
        if ( nOther.isA() )
          return swapCase4( nPar, nSwap, nOther )
        if ( nOther.isB() && nOther.getL() == nOther.getR() )
          return swapCase6( nPar, nSwap, nOther )
        if ( nOther.isB() )
          return swapCase7( nPar, nSwap, nOther )
      }

      if ( nOther.isB() && nOther.getL() == nOther.getR() ) {
        if ( nSwap.isA() )
          return swapCase4( nPar, nOther, nSwap )
        if ( nSwap.isB() )
          return swapCase7( nPar, nOther, nSwap )
      }

      if ( nSwap.isA() && nOther.isA() )
        return swapCase5( nPar, nSwap, nOther )
    }

    if ( nPar.isB() ) {
      if ( nSwap.isB() && nSwap.getL() == nSwap.getR() ) {
        if ( nOther.isB() && nOther.getL() == nOther.getR() )
          return swapCase10( nPar, nSwap, nOther )
        if ( nOther.isB() )
          return swapCase13( nPar, nSwap, nOther )
        return List[Node]()
      }

      if ( nOther.isB() && nOther.getL() == nOther.getR() ) {
        if ( nSwap.isB() )
          return swapCase13( nPar, nOther, nSwap )
      }

      if ( nSwap.isB() && nOther.isB() )
        return swapCase12( nPar, nSwap, nOther )

      if ( nSwap.isA() && nOther.isA() )
        return swapCase11( nPar, nSwap, nOther )
    }
    List[Node]()
  }

}