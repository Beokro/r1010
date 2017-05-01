/* ============================================================================
 *  Checker.java
 * ============================================================================
 * 
 *  Authors:			(c) 2014 Irene Finocchi, Marco Finocchi, Emanuele G. Fusco
 *  Description:		utility functions to compare nodes based on their degree
 *  					
 */

package search10;

public class Checker {

	public static boolean DoubleCheck(String node1,int degree_node1,String node2,int degree_node2) {
	
		if(degree_node1 < degree_node2)
			return true;
	
		else {
		
			if((degree_node1 == degree_node2) && ((node1.compareTo(node2)) < 0))
				return true;
		
			else return false;
		
		}
	
	}

	public static boolean TripleCheck(String node1,int degree_node1,String node2,int degree_node2,String node3, int degree_node3) {
		
		if((degree_node1 < degree_node2) && (degree_node1 < degree_node3))
			return true;
		
		else {
			
			if((degree_node1 == degree_node2) && (degree_node1 < degree_node3)) {
				
				if((node1.compareTo(node2)) < 0)
					return true;
				
				else return false;
				
			}
			
			else {
				
				if((degree_node1 < degree_node2) && (degree_node1 == degree_node3)) {
					
					if((node1.compareTo(node3)) < 0)
						return true;
					
					else return false;
					
				}
				
				else {
					
					if((degree_node1 == degree_node2) && (degree_node1 == degree_node3))
						
						if(((node1.compareTo(node2)) < 0) && ((node1.compareTo(node3))<0))
							return true;
							
						else return false;
						
					else return false;
					
				}
				
			}
			
		}
		
	}

}
