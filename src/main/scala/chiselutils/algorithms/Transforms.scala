/** This file implements valid transformations on
  * a different numbers of Nodes
  */
package chiselutils.algorithms

import collection.mutable.ArrayBuffer
import scala.util.Random
import scala.collection.generic.CanBuildFrom
import scala.language.{implicitConversions, higherKinds}

object Transforms {

  val useOnlyAdd2 = false
  val myRandT = java.util.concurrent.ThreadLocalRandom.current() //new Random
  val myRand = new Random

  /** Combine two uks into 1 and return the index mapping to them
    */
  def commonMapping( uKa : Seq[Set[Seq[Int]]], uKb : Seq[Set[Seq[Int]]]) :
      ( Seq[Set[Seq[Int]]], Seq[Int], Seq[Int] ) = {
    val uKc = (uKa ++ uKb).distinct
    val aIdxMap = uKa.map( uki => uKc.indexOf( uki ) )
    val bIdxMap = uKb.map( uki => uKc.indexOf( uki ) )
    ( uKc.toVector, aIdxMap.toVector, bIdxMap.toVector )
  }

  /** Look at two nodes and try to merge them
    * return new node if merge was done
    * seach parents for common l/r
    * check overlap and that constraint type the same
    * create new merged node
    * look if all L or all R for mux. if so set l/r
    */
  def tryMerge( nA : Node , nB : Node ) : Option[Node] = {

    // checkType
    if ( nA.isA() != nB.isA() || nA.isB() != nB.isB() || nA.isC() != nB.isC() )
      return None

    if ( nA.isA() && nA.getChildren() != nB.getChildren() )
      return None

    // if B
    if ( !nA.getChildren().subsetOf( nB.getChildren() ) && !nB.getChildren().subsetOf( nA.getChildren() ) )
      return None

    val mapping = commonMapping( nA.uk, nB.uk )
    val ck = ( nA.ck zip nB.ck ).map( cki => {
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
    if ( nA.isA() )
      nC.setA()
    if ( nA.isB() )
      nC.setB()
    if ( nA.isC() )
      nC.setC()

    // set children
    nC.addChildren( nA.getChildren() ++ nB.getChildren() )

    // fix parent links
    for ( p <- nA.getParents() ++ nB.getParents() ) {
      p.replaceIfChild( nA, nC )
      p.replaceIfChild( nB, nC )
    }

    return Some( nC )
  }

  /** Implement random functions so can change to better random source easily
    */
  private def randBool() : Boolean = {
    randInt( 2 ) == 0
  }

  private def randInt( n : Int ) : Int = {
    myRandT.nextInt( 0,  n )
  }

  private def randShuffle[T, CC[X] <: TraversableOnce[X]]( l : CC[T] )(implicit bf: CanBuildFrom[CC[T], T, CC[T]]) : CC[T] = {
    /*
    val ar = ArrayBuffer[T]() ++ l

    for ( i <- 0 until ar.size ) {
      val index = randInt( i + 1 );
      // Simple swap
      val a = ar( index );
      ar( index ) = ar( i );
      ar( i ) = a;
    }
    ar.to[CC]
     */
    myRand.shuffle( l )
  }

  /** Combine two uk/cks in an add
    */
  private def combineAdd( uk1 : Seq[Set[Seq[Int]]], ck1 : Seq[Int],
    uk2 : Seq[Set[Seq[Int]]], ck2 : Seq[Int] ) : ( Seq[Set[Seq[Int]]], Seq[Int] ) = {
    val ckCombined = ck1.zip( ck2 )
    val uKidx = ckCombined.distinct.filter( _ != ( -1, -1 ) )
    val ckNew = ckCombined.map( uKidx.indexOf( _ ) )
    // should never have one as -1 and not the other
    assert( !uKidx.find( i => i._1 == -1 || i._2 == -1 ).isDefined,
      "Invalid add combine of {" + uk1 + ", " + ck1 + "} and {" + uk2 + ", " + ck2 + "}" )
    val ukNew = uKidx.map( uki => uk1( uki._1 ) ++ uk2( uki._2 ) )
    ( ukNew, ckNew )
  }

  /** Combine two uk/cks in a mux
    */
  private def combineMux( uk1 : Seq[Set[Seq[Int]]], ck1 : Seq[Int],
    uk2 : Seq[Set[Seq[Int]]], ck2 : Seq[Int] ) : ( Seq[Set[Seq[Int]]], Seq[Int] ) = {
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
        assert( uk1Idx( cks._1 ) == uk2Idx( cks._2 ),
          "Invalid mux combine of {" + uk1 + ", " + ck1 + "} and {" + uk2 + ", " + ck2 + "}" )
        uk1Idx( cks._1 )
      }
    })
    ( ukNew, ckNew )
  }

  /** pass ck through the ckFilter so that cks with other parents aren't included
    */
  private def filterCk( ck : Seq[Int], uk : Seq[Set[Seq[Int]]], ckFilter : Seq[Int], ukFilter : Seq[Set[Seq[Int]]] ) : Seq[Int] = {
    val mapping = ukFilter.map( uki => uk.indexOf( uki ) )
    val parCk = ckFilter.map( cki => if ( cki != -1 ) mapping( cki ) else -1 )
    parCk.zip( ck ).map( cks => if ( cks._2 == -1 || cks._1 != cks._2 ) -1 else cks._2 )
  }

  /** ignore uks, useful for adds with no other parents
    */
  private def filterCk( ck : Seq[Int], ckFilter : Seq[Int] ) : Seq[Int] = {
    ckFilter.zip( ck ).map( cks => if ( cks._1 == -1 ) -1 else cks._2 )
  }

  private def filterCk( child : Node, par : Node ) : Seq[Int] = {
    if ( par.isB() )
      return filterCk( child.getCkNext, child.getUkNext, par.ck, par.uk )
    filterCk( child.getCkNext, par.ck ) // for adds just filter -1's
  }

  /** nPar is a reg, input is mux
    * goes to case 10
    */
  private def swapCase2( nPar : Node ) : Seq[Node] = {
    assert( nPar.numChildren() == 1, "Node must have one child" )
    val nMux = nPar.getOnlyChild()
    val newNodes = nMux.getChildren().map( child => {
      val ck = filterCk( child, nMux )
      val newNode = Node( child.getUkNext, ck )
      newNode.addChild( child )
      newNode.setB()
      newNode
    })
    nMux.removeChildren()
    nPar.removeChildren()
    nPar.addChildren( newNodes )
    nPar.setB()
    List( nPar ) ++ newNodes.toList
  }

  /** nPar is add, inputs are adds
    * can go to 1, 4, 5, 6, 20, 21
    */
  private def swapCase5( nPar : Node, applyIfIncrease : Boolean = true ) : Seq[Node] = {

    val allGrandList = nPar.getChildren().map( c => c.getChildren().map( gc => ( gc, c ) ) ).reduce( _ ++ _ ).toList
    val allGc = randShuffle( allGrandList )

    val addSize = {
      if ( useOnlyAdd2 )
        2
      else
        3
    }
    val noGrps = addSize - 1
    var minVal = math.max( allGc.size - addSize*noGrps, 0 )
    var maxVal = math.min( allGc.size, addSize )
    val group0Idx = randInt( maxVal + 1 - minVal ) + minVal
    minVal = math.max( allGc.size - addSize * ( noGrps - 1 ) - group0Idx, 0 )
    maxVal = math.min( allGc.size - group0Idx, addSize ) 
    val group1Idx = randInt( maxVal + 1 - minVal ) + minVal + group0Idx
    val allGcGrouped = List(
      ( 0 until group0Idx ).map( allGc( _ ) ),
      ( group0Idx until group1Idx ).map( allGc( _ ) ),
      ( group1Idx until allGc.size ).map( allGc( _ ) )
    ).filter( !_.isEmpty )
    if ( !applyIfIncrease && nPar.numChildren() < allGcGrouped.size )
      return List[Node]()

    val addNodes = allGcGrouped.map( addGroup => {
      val ukck = addGroup.map( gcc => {
        ( gcc._1.getUkNext, filterCk( gcc._1, gcc._2 ) )
      }).reduce( ( x, y ) => combineAdd( x._1, x._2, y._1, y._2 ) )
      val newNode = Node( ukck._1, ukck._2 )
      newNode.addChildren( addGroup.map( _._1 ).toSet )
      if ( addGroup.size > 1 )
        newNode.setA()
      else
        newNode.setB()
      newNode
    }).toSet
    for ( c <- nPar.getChildren )
      c.removeChildren()
    nPar.removeChildren()
    nPar.addChildren( addNodes )
    if ( addNodes.size == 1 )
      nPar.setB()
    else
      nPar.setA()
    List( nPar ) ++ addNodes.toList
  }

  /** nPar is add, inputs are reg and mux
    * go to case 8, 11
    */
  private def swapCase7( nPar : Node, nReg : Node, nMux : Node ) : Seq[Node] = {

    val nMuxUkCk = nMux.getChildren().map( child => {
      val newcK = filterCk( child, nMux )
      val newuK = child.getUkNext
      ( newuK, newcK, child )
    }).toList

    val newNodes = {
      if ( randBool() ) { // randomally choose case 8
        val regFilt = nMuxUkCk.map( ukck => {
          ( nReg.uk, filterCk( nReg.ck, ukck._2 ) )
        })
        nPar.setA()
        regFilt.zip( nMuxUkCk.reverse ).map( pair => {
          val regUkCk = pair._1
          val muxUkCk = pair._2
          val ukck = combineMux( regUkCk._1, regUkCk._2, muxUkCk._1, muxUkCk._2 )
          val nodeA = Node( ukck._1, ukck._2 )
          nodeA.addChild( muxUkCk._3 )
          nodeA.addChild( nReg.getOnlyChild() )
          nodeA.setB()
          nodeA
        })
      } else { // go to case 11
        nPar.setB()
        nMuxUkCk.map( ukck => {
          val swapCk = filterCk( nReg.ck, ukck._2 )
          val combAdd = combineAdd( ukck._1, ukck._2, nReg.uk, swapCk )
          val nodeA = Node( combAdd._1, combAdd._2 )
          nodeA.addChild( ukck._3 )
          nodeA.addChild( nReg.getOnlyChild() )
          nodeA.setA()
          nodeA
        })
      }
    }

    nReg.removeChildren()
    nMux.removeChildren()
    nPar.removeChildren()
    nPar.addChildren( newNodes )
    List( nPar ) ++ newNodes.toList
  }


  /** nPar is add, others are mux
    * goes to case 7, 11, 16, 17
    */
  private def swapCase8( nPar : Node ) : Seq[Node] = {
    // can only transform if two unique states, so try to find
    val nParChildren = nPar.getChildren().toList
    val nMuxCk = nPar.getChildren.map( c => c.getChildren().map( gc => {
      ( gc.getUkNext, filterCk( gc, c ), gc, c )
    })).toList.sortBy( -_.size ) // make sure mux is first

    val correspondingCk = nMuxCk.head.map( nodeInfo => {
      val matchCks = nMuxCk.tail.map( nMuxSearch => {
        if ( nMuxSearch.size == 1 ) // there is just a reg here which is valid for both sides of mux
          Some( nMuxSearch.head )
        else { // it is a mux so ck must exactly match
          nMuxSearch.find( otherInfo => {
            nodeInfo._2 == filterCk( nodeInfo._2, otherInfo._2 ) &&
            otherInfo._2 == filterCk( otherInfo._2, nodeInfo._2 )
          })
        }
      })
      ( nodeInfo, matchCks )
    })
    val noPairs = correspondingCk.find( m => m._2.find( z => !z.isDefined ).isDefined ).isDefined

    if ( noPairs ) // can't do anything
      return List[Node]()

    // should have matched nodes
    val addNodes = correspondingCk.map( nodeMatch => {
      val nMuxParts = List( nodeMatch._1 ) ++ nodeMatch._2.map( _.get )
      val ukckSet = nMuxParts.map( nMux => ( nMux._1, filterCk( nMux._2, nodeMatch._1._2 ) ) )
      val ukck = ukckSet.reduce( (x, y) => combineAdd( x._1, x._2, y._1, y._2 ) )
      val n = Node( ukck._1, ukck._2 )
      n.setA()
      n.addChildren( nMuxParts.map( _._3 ) )
      n
    })
    for ( c <- nPar.getChildren )
      c.removeChildren()
    nPar.removeChildren()
    nPar.addChildren( addNodes )
    nPar.setB()
    List( nPar ) ++ addNodes
  }

  /** nPar is add, inputs are add and mux
    * goes to 11, 16, 18
    */
  private def swapCase9( nPar : Node, nAdd : Node, nMux : Node ) : Seq[Node] = {
    if ( useOnlyAdd2 ) // there is no way to transform 9 using only add2's
      return List[Node]()
    val newNodes = {
      if ( nAdd.isAdd3() || randBool() ) {
        // simply partition the add
        val randChild = nAdd.getRandomChild()
        val otherChild = randChild._2.toList
        val n1Ck = filterCk( randChild._1, nAdd )
        val n1 = Node( randChild._1.getUkNext, n1Ck )
        n1.addChild( randChild._1 )
        n1.setB()
        val n2ukck = otherChild.map( x => ( x.getUkNext, filterCk( x, nAdd ) ) ).reduce( ( x, y ) => {
          combineAdd( x._1, x._2, y._1, y._2 )
        })
        val n2 = Node( n2ukck._1, n2ukck._2 )
        if ( otherChild.size == 1 )
          n2.setB()
        else
          n2.setA()
        n2.addChildren( otherChild )
        nPar.setA()
        List( n1, n2, nMux )
      } else {
        val ukcks = nMux.getChildren().map( child => {
          val newCk = filterCk( child, nMux )
          val addCk = filterCk( nAdd.ck, newCk )
          val ukck = combineAdd( child.getUkNext, newCk, nAdd.uk, addCk )
          ( ukck._1, ukck._2, child )
        })
        val newNodes = ukcks.map( ukck => {
          val n = Node( ukck._1, ukck._2 )
          n.addChildren( nAdd.getChildren() )
          n.addChild( ukck._3 )
          n.setA()
          n
        })
        nMux.removeChildren()
        nPar.setB()
        newNodes
      }
    }
    nAdd.removeChildren()
    nPar.removeChildren()
    nPar.addChildren( newNodes )
    List( nPar ) ++ newNodes.toList
  }

  /** nPar is mux, inputs are reg
    * go to case 2
    */
  private def swapCase10( nPar : Node ) : Seq[Node] = {

    val ukck = nPar.getChildren().map( child => {
      val grandchild = child.getOnlyChild()
      val cK = filterCk( grandchild, child )
      val uK = grandchild.getUkNext
      ( uK, cK )
    })
    val combMux = ukck.reduce( ( x, y ) => combineMux( x._1, x._2, y._1, y._2 ) )
    val nodeA = Node( combMux._1, combMux._2 )
    nodeA.addChildren( nPar.getChildren().map( _.getOnlyChild() ) )
    nodeA.setB()
    for ( n <- nPar.getChildren() )
      n.removeChildren()
    nPar.removeChildren()
    nPar.addChild( nodeA )
    nPar.setB()
    List( nPar, nodeA )
  }

  /** nPar is Mux, inputs are adds
    * go to case 1, 6, 7, 8, 9, 16, 17
    */
  private def swapCase11( nPar : Node, applyIfIncrease : Boolean = true ) : Seq[Node] = {
    val nParChildren = nPar.getChildren().toList
    val nAdd1 = nParChildren(0)
    val nAdd2 = nParChildren(1)
    // find common
    val commonChildren = nAdd1.getChildren() intersect nAdd2.getChildren()
    // can be 2 and 2 or 3 and 3 but not 2 and 3
    if ( nAdd1.numChildren() == nAdd2.numChildren() ) {
      val regUkCk = commonChildren.map( child => {
        val ckS = filterCk( child, nAdd1 )
        val ckO = filterCk( child, nAdd2 )
        val muxComb = combineMux( child.getUkNext, ckS, child.getUkNext, ckO )
        ( muxComb._1, muxComb._2, child )
      })
      val combineRegNodes = regUkCk.size == 2 && randBool()
      if ( !applyIfIncrease && nAdd1.numChildren() == 3 && !combineRegNodes )
        return List[Node]() // will increase the amount of hardware
      val otherChildS = nAdd1.getChildren() -- commonChildren
      val otherChildO = nAdd2.getChildren() -- commonChildren
      val muxComb = otherChildS.zip( otherChildO ).map( x => {
        val sck = filterCk( x._1, nAdd1 )
        val ock = filterCk( x._2, nAdd2 )
        val muxUkCk = combineMux( x._1.getUkNext, sck, x._2.getUkNext, ock )
        ( muxUkCk._1, muxUkCk._2, x._1, x._2 )
      })
      val muxCombNodes = muxComb.map( muxUkCk => {
        val newNode = Node( muxUkCk._1, muxUkCk._2 )
        newNode.addChild( muxUkCk._3 )
        newNode.addChild( muxUkCk._4 )
        newNode.setB()
        newNode
      })
      val regNodes = {
        if ( combineRegNodes ) {
          val ukck = regUkCk.map( x => ( x._1, x._2 ) ).reduce( (x, y) => {
            combineAdd( x._1, x._2, y._1, y._2 )
          })
          val n = Node( ukck._1, ukck._2 )
          n.addChildren( regUkCk.map( _._3 ) )
          n.setA()
          Set( n )
        } else {
          regUkCk.map( muxComb => {
            val n = Node( muxComb._1, muxComb._2 )
            n.addChild( muxComb._3 )
            n.setB()
            n
          })
        }
      }
      nAdd1.removeChildren()
      nAdd2.removeChildren()
      nPar.removeChildren()
      nPar.addChildren( muxCombNodes ++ regNodes )
      if ( nPar.numChildren() == 1 )
        nPar.setB()
      else
        nPar.setA()
      return List( nPar ) ++ regNodes ++ muxCombNodes
    }
    List[Node]()
  }

  /** nPar is mux, inputs are muxes
    * go to case 10, 12, 13
    */
  private def swapCase12( nPar : Node ) : Seq[Node] = {
    swapCase13( nPar ) // these cases are actually equivalent
  }

  /** nPar is mux, inputs are mux and reg ( can also be mux and mux as same code for 12 )
    * go to case 10, 12, 13
    */
  private def swapCase13( nPar : Node ) : Seq[Node] = {
    val allGc = nPar.getChildren().map( c => c.getChildren().map( gc => ( gc, c ) ) ).reduce( _ ++ _ )
    val allGcShuffled = randShuffle( allGc.toList )
    var addedNode : Option[(Node, Node)] = None
    val muxGrps = allGcShuffled.grouped( 2 ).map( grp => {
      val newGrp = {
        if ( grp.size == 2 || randBool() )
          grp
        else {
          // pick a random node from allGc, may or may not be the same
          val idx = randInt( allGc.size )
          val chosenNode = allGc.iterator.drop( idx ).next
          val moreThan1Uki = filterCk( chosenNode._1, chosenNode._2 ).distinct.filter( _ != -1 ).size > 1
          if ( !grp.contains( chosenNode ) && moreThan1Uki ) {
            addedNode = Some( chosenNode )
            grp ++ List( chosenNode )
          } else
            grp
        }
      }
      val cks = newGrp.map( pair => {
        val ck = filterCk( pair._1, pair._2 )
        ( pair._1.getUkNext, ck )
      })
      ( newGrp, cks )
    }).toList
    val cksMod = {
      if ( addedNode.isDefined ) {
        // split on uks first, if only 1 uk then split on ck
        val uk = addedNode.get._1.getUkNext
        val ck = filterCk( addedNode.get._1, addedNode.get._2 )
        val distinctCk = randShuffle( ck.distinct.filter( _ != -1 ) )
        assert( distinctCk.size > 1, "Distinct ck size should be more than 1 for node to be added" )
        val ckPartition = distinctCk.splitAt( distinctCk.size/2 )
        val ck1 = ck.map( cki => if ( ckPartition._1.contains( cki ) ) cki else -1 )
        val ck2 = ck.map( cki => if ( ckPartition._2.contains( cki ) ) cki else -1 )
        var replaced1 = false
        muxGrps.map( muxComb => {
          val cks = muxComb._2
          cks.map( ukck => {
            if ( ukck._1 == uk && ukck._2 == ck ) {
              if ( replaced1 )
                ( uk, ck2 )
              else {
                replaced1 = true
                ( uk, ck1 )
              }
            } else
              ukck
          })
        })
      } else
        muxGrps.map( _._2 )
    }
    val newMuxes = muxGrps.map( _._1 ).zip( cksMod ).map( muxComb => {
      val newGrp = muxComb._1
      val cks = muxComb._2
      // randomally partition cks so that nothing is repeated
      val ukck = cks.reduce( ( x, y ) => combineMux( x._1, x._2, y._1, y._2 ) )
      val newNode = Node( ukck._1, ukck._2 )
      newNode.setB()
      newNode.addChildren( newGrp.map( _._1 ) )
      newNode
    }).toSet
    for ( c <- nPar.getChildren() )
      c.removeChildren()
    nPar.removeChildren()
    nPar.addChildren( newMuxes )
    nPar.setB()
    List( nPar ) ++ newMuxes
  }

  /** 3 input add parent, reg, reg, mux childs
    * go to case 9, 11
    */
  private def swapCase16( nPar : Node ) : Seq[Node] = {
    val commonNodes = nPar.getChildren().filter( _.isReg() )
    val muxNode = ( nPar.getChildren() -- commonNodes ).head
    val ukckReg = commonNodes.map( child => {
      ( child.uk, child.ck )
    }).reduce( (x,y) => combineAdd( x._1, x._2, y._1, y._2 ) )
    val newAdds = {
      if ( randBool() ) {
        val newAdds = muxNode.getChildren().map( child => {
          val ck = filterCk( child, muxNode )
          val ckFiltered = filterCk( ukckReg._2, ck )
          val ukck = combineAdd( child.getUkNext, ck, ukckReg._1, ckFiltered )
          val addNode = Node( ukck._1, ukck._2 )
          addNode.addChild( child )
          addNode.addChildren( commonNodes.map( _.getOnlyChild() ) )
          addNode.setA()
          addNode
        })
        muxNode.removeChildren()
        nPar.setB()
        newAdds
      } else {
        // just combine to an add and leave the mux alone
        val nAdd = Node( ukckReg._1, ukckReg._2 )
        nAdd.addChildren( commonNodes.map( _.getOnlyChild() ) )
        nAdd.setA()
        nPar.setA()
        Set( muxNode, nAdd )
      }
    }
    for ( c <- commonNodes )
      c.removeChildren()
    nPar.removeChildren()
    nPar.addChildren( newAdds )
    List( nPar ) ++ newAdds.toList
  }

  /** nPar is add, inputs are reg, add and mux
    * go to case 9
   */
  private def swapCase18( nPar : Node, nReg : Node, nMux : Node, nAdd : Node ) : Seq[Node] = {
    if ( nAdd.numChildren() == 2 ) {
      // combine the reg and 2 input add to a 3 input add
      val ukck = combineAdd( nReg.uk, nReg.ck, nAdd.uk, nAdd.ck )
      val newAdd = Node( ukck._1, ukck._2 )
      newAdd.setA()
      newAdd.addChild( nReg.getOnlyChild() )
      newAdd.addChildren( nAdd.getChildren() )
      nReg.removeChildren()
      nAdd.removeChildren()
      nPar.replaceIfChild( nReg, newAdd )
      nPar.replaceIfChild( nAdd, newAdd )
      return List( nPar, newAdd, nMux )
    }
    // else 3 input, turn into 2 X 2 input
    val addSplit = nAdd.getRandomChild()
    val addCk = filterCk( addSplit._1.getCkNext, nAdd.ck )
    val ukck = combineAdd( addSplit._1.getUkNext, addCk, nReg.uk, nReg.ck )
    val newAddA = Node( ukck._1, ukck._2 )
    newAddA.addChild( addSplit._1 )
    newAddA.addChild( nReg.getOnlyChild() )
    newAddA.setA()
    val remainChildA = addSplit._2.head
    val rCA_ck = filterCk( remainChildA.getCkNext, nAdd.ck )
    val remainChildB = addSplit._2.tail.head
    val rCB_ck = filterCk( remainChildB.getCkNext, nAdd.ck )
    val ukckB = combineAdd( remainChildA.getUkNext, rCA_ck, remainChildB.getUkNext, rCB_ck )
    val newAddB = Node( ukckB._1, ukckB._2 )
    newAddB.addChild( remainChildA )
    newAddB.addChild( remainChildB )
    newAddB.setA()
    nReg.removeChildren()
    nAdd.removeChildren()
    nPar.replaceIfChild( nReg, newAddA )
    nPar.replaceIfChild( nAdd, newAddB )
    List( nPar, newAddA, newAddB, nMux )
  }

  /** npar is add, inputs are add, add and mux
    * go to case 18, 19
    */
  private def swapCase19( nPar : Node, nAdd1 : Node, nAdd2 : Node, nMux : Node ) : Seq[Node] = {
    assert( nAdd1.numChildren() >= nAdd2.numChildren(), "nAdd1 should have more children" )
    val chosenChild = nAdd1.getRandomChild()
    val chosenChild2 = nAdd2.getRandomChild()
    if ( nAdd1.numChildren() == 3 ) {
      // take a random child from nAdd1 and put with nAdd2
      val childList1 = chosenChild._2.toList ++ List( chosenChild2._1 )
      val childList2 = chosenChild2._2.toList ++ List( chosenChild._1 )
      val newAdds = List( childList1, childList2 ).map( childList => {
        val ukck = childList.map( n => {
          val nCk = {
            if ( nAdd1.getChildren().contains( n ) )
              filterCk( n.getCkNext, nAdd1.ck )
            else
              filterCk( n.getCkNext, nAdd2.ck )
          }
          ( n.getUkNext, nCk )
        }).reduce( (x, y ) => combineAdd( x._1, x._2, y._1, y._2 ) )
        val newAdd = Node( ukck._1, ukck._2 )
        newAdd.addChildren( childList )
        newAdd.setA()
        newAdd
      })
      nAdd1.removeChildren()
      nAdd2.removeChildren()
      nPar.removeChildren()
      nPar.addChildren( newAdds ++ List( nMux ) )
      return List( nPar, nMux ) ++ newAdds
    }
    // else both 2 input adds
    val cC_cK = filterCk( chosenChild._1.getCkNext, nAdd1.ck )
    val ukck = combineAdd( nAdd2.uk, nAdd2.ck, chosenChild._1.getUkNext, cC_cK )
    val newAdd2 = Node( ukck._1, ukck._2 )
    newAdd2.addChildren( nAdd2.getChildren() )
    newAdd2.addChild( chosenChild._1 )
    newAdd2.setA()
    val regNode = chosenChild._2.head
    val regCk = filterCk( regNode.getCkNext, nAdd1.ck )
    val newReg = Node( regNode.getUkNext, regCk )
    newReg.addChild( regNode )
    newReg.setB()
    nAdd2.removeChildren()
    nAdd1.removeChildren()
    nPar.replaceIfChild( nAdd1, newReg )
    nPar.replaceIfChild( nAdd2, newAdd2 )
    List( nPar, nMux, newReg, newAdd2 )
  }

  /** Look at two nodes and try to swap them
    */
  def trySwap( nPar : Node, applyIfIncrease : Boolean = true ) : (Seq[Node], Int) = {

    if ( nPar.getChildren().find( _.isC() ).isDefined )
      return (List[Node](), 0)

    // work out how nodes are connected ( should be directly )
    if ( nPar.isReg() ) {
      if ( nPar.getOnlyChild().isAdd() )
        return ( swapCase5( nPar, applyIfIncrease ), 1 )// return { if ( applyIfIncrease ) ( swapCase1( nPar ), 1 ) else ( List[Node](), 1 ) }
      if ( nPar.getOnlyChild().isMux() )
        return { if ( applyIfIncrease ) ( swapCase2( nPar ), 2 ) else ( List[Node](), 2 ) }
      // case 3: two regs so no point as changes nothing
      return (List[Node](), 3)
    }

    val nReg = nPar.getChildren().find( _.isReg() )
    val nAdd = nPar.getChildren().find( _.isAdd() )
    val nMux = nPar.getChildren().find( _.isMux() )

    if ( nPar.isAdd2() ) {
      if ( nReg.isDefined && nAdd.isDefined )
        return ( swapCase5( nPar, applyIfIncrease ), 4 ) //return ( swapCase4( nPar, nReg.get, nAdd.get ), 4 )
      if ( nAdd.isDefined && !nMux.isDefined && !nReg.isDefined) // must be 2 adds
        return ( swapCase5( nPar, applyIfIncrease ), 5 )
      if ( nReg.isDefined && !nMux.isDefined && !nAdd.isDefined ) // must be 2 regs
        return ( swapCase5( nPar, applyIfIncrease ), 6 ) // ( swapCase6( nPar ), 6 )
      if ( nReg.isDefined && nMux.isDefined )
        return ( swapCase7( nPar, nReg.get, nMux.get ), 7 )
      if ( nMux.isDefined && !nReg.isDefined && !nAdd.isDefined ) // must be 2 mux
        return ( swapCase8( nPar ), 8 )
      if ( nAdd.isDefined && nMux.isDefined )
        return ( swapCase9( nPar, nAdd.get, nMux.get ), 9 )
      // should never reach here
      assert( false, "Parent:" + nPar + ", children = " + nPar.getChildren() )
    }

    if ( nPar.isMux() ) {
      if ( nReg.isDefined && !nMux.isDefined && !nAdd.isDefined )
          return ( swapCase10( nPar ), 10 )
      if ( nAdd.isDefined && !nMux.isDefined && !nReg.isDefined )
        return ( swapCase11( nPar, applyIfIncrease ), 11 )
      if ( nMux.isDefined && !nReg.isDefined && !nAdd.isDefined )
        return ( swapCase12( nPar ), 12 )
      if ( nReg.isDefined && nMux.isDefined )
        return ( swapCase13( nPar ), 13 )
      if ( nReg.isDefined && nAdd.isDefined )
        return ( List[Node](), 14 ) // case 14: reg and add, cant do anything
      if ( nMux.isDefined && nAdd.isDefined )
        return ( List[Node](), 15 ) // case 15: mux and add, cant do anything
      // should never reach here
      assert( false, "Parent:" + nPar + ", children = " + nPar.getChildren() )
    }

    if ( nPar.isAdd3() ) {
      if ( nAdd.isDefined && !nMux.isDefined && !nReg.isDefined )
        return ( swapCase5( nPar, applyIfIncrease ), 5 )
      if ( nReg.isDefined && !nMux.isDefined && !nAdd.isDefined )
        return ( swapCase5( nPar, applyIfIncrease ), 6 )
      if ( nReg.isDefined && nMux.isDefined && nAdd.isDefined )
        return ( swapCase18( nPar, nReg.get, nMux.get, nAdd.get ), 18 )
      if ( nReg.isDefined && nMux.isDefined ) {
        if ( nPar.getChildren().filter( _.isReg() ).size == 2 ) // if 2 reg then case 16
          return ( swapCase16( nPar ), 16 )
        return ( swapCase8( nPar ), 17 ) // case 17: 2 mux and reg
      }
      val adds = nPar.getChildren().filter( _.isAdd() ).toList.sortBy( x => -x.numChildren() )
      if ( nAdd.isDefined && nMux.isDefined ) {
        if ( adds.size == 2 ) // if 2 add then case 19
          return ( swapCase19( nPar, adds(0), adds(1), nMux.get ), 19 )
        // case 22: 2 mux and add, cant do anything
        return ( List[Node](), 22 )
      }
      if ( nReg.isDefined && nAdd.isDefined ) {
        if ( adds.size == 2 ) // if 2 add then case 20, if 2 reg case 21
          return ( swapCase5( nPar, applyIfIncrease ), 20 ) // ( swapCase20( nPar, adds(0), adds(1), nReg.get ), 20 )
        return ( swapCase5( nPar, applyIfIncrease ), 21 ) // ( swapCase21( nPar ), 21 )
      }
      if ( nMux.isDefined ) // 3 muxes
        return ( swapCase8( nPar ), 23 )
    }

    // should never reach here
    assert( false, "Parent:" + nPar + ", children = " + nPar.getChildren() )

    ( List[Node](), 0 )
  }

  /** Split a node and randomally assign its parents to each
    */
  def trySplit( nA : Node ) : Seq[Node] = {
    if ( nA.getParents().size <= 1 || nA.isC() )
      return List[Node]()

    for ( n <- List( nA ) ++ nA.getParents() ++ nA.getChildren )
      assert( n.isLocked() )

    val shuffledPar = randShuffle( nA.getParents() )
    val splitIdx = randInt( shuffledPar.size - 1 )

    val ckNeeded = shuffledPar.map( n => {
      if ( n.isAdd() )
        filterCk( nA.ck, n.getCkPrev )
      else
        filterCk( nA.ck, nA.uk, n.getCkPrev, n.getUkPrev )
    })

    val ( parSplit1, parSplit2 ) = shuffledPar.zip( ckNeeded ).splitAt( splitIdx + 1 )
    val newNodes = List( parSplit1, parSplit2 ).map( parSplit => {
      val nCk = ( 0 until nA.nodeSize ).map( idx => {
        parSplit.map( cks => cks._2( idx ) ).reduce( (x,y) => {
          if ( x == -1 )
            y
          else {
            assert( y == x || y == -1, "Invalid combining of parents" )
            x
          }
        })
      }).toVector
      assert( nCk.filter( _ != -1 ).size > 0, "Trying to create empty node from " + nA +
        ", shuffledPar = " + shuffledPar + ", splitIdx = " + splitIdx )
      val n = Node( nA.uk, nCk )
      if ( nA.isA() )
        n.setA()
      if ( nA.isB() )
        n.setB()
      // if nA is mux then check so not adding useless children
      if ( nA.isB() ) {
        n.addChildren( nA.getChildren().filter( c => n.isUsefulChild(c) ) )
        assert( n.numChildren() > 0, "Must have atleast 1 useful child for " + n + " from " + nA )
      } else
        n.addChildren( nA.getChildren() )
      for ( p <- parSplit )
        p._1.replaceIfChild( nA, n )
      n
    })
    nA.removeChildren()
    newNodes
  }

}
