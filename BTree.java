import java.io.*;
import java.lang.*;
import java.util.*;

public class BTree{
	public static long btNumRec = 0;
	public static final long INIT_POS = 0;
	public static final long INIT_VAL = 0;
	public static final long DEF_VALUE = -1;
	public static final long IND_FIRST_CHILD = 1;
	public static final int ORDER = 7;
	public static final long BYTES_PER_ENTRY = 8;
	public static final long NOT_PARENT = -1;
	public static final long BYTES_PER_NODE = ((3*ORDER)-1)*BYTES_PER_ENTRY;
	public static final int HEADER_BYTES = 16;
	public static final int LAST_OFFSET = 18;
	public static final int NUM_POINTERS = 3*ORDER-1;
	public static final long MAX_KEYS = ORDER - 1;
	public static long END_OF_FILE = BYTES_PER_NODE*(btNumRec); 
	public static final int MAX_SPLIT_NODES = 2;
	public static long root = 0;
	public static ValueStore vs;
	public static long cvs;
	public static RandomAccessFile raf;
	public static Stack<Long> splitChildren;

	//constructor
	public BTree(String strFile, ValueStore valueS) throws IOException{
		File file = new File(strFile);
		if(!file.exists()){
			//make raf and file
			raf = new RandomAccessFile(strFile, "rwd");
			file.createNewFile();


			//when creating, putting first value as well
			raf.writeLong(INIT_VAL); //number of records
			raf.writeLong(INIT_VAL); //position of root node
			initRec(INIT_POS);
			btNumRec++;
		}else{
			raf = new RandomAccessFile(file, "rwd");
		}
		vs = valueS;

		splitChildren = new Stack<Long>();
	}

	public void initRec(long index) throws IOException {
		//Fills up a node with -1s
		for(int i = 0; i < NUM_POINTERS; i++){
			raf.seek(HEADER_BYTES + btNumRec*BYTES_PER_NODE + i*BYTES_PER_ENTRY);
			raf.writeLong(DEF_VALUE);
		}
	}

	public String addValue(long key, long vNumRec, long corrNode) throws IOException{
		String verdict = "";
		cvs = vNumRec; //vsNumRec of key to be inserted
		//check if number of nodes is still 0
		//change number of nodes to 1 if yes (first value to be inserted into file)
		raf.seek(INIT_POS);
		if(raf.readLong()==0){
			raf.seek(INIT_POS);
			raf.writeLong(1);
		}

		//finds correct node/bt record number
		long initCorrRec;
		if(corrNode == DEF_VALUE){
			//new insert
			initCorrRec = findCorrectNode(key);
			if(isCopy(key) != DEF_VALUE){
				verdict = "ERROR: " + key + " already exists.";
				return verdict;
			}
		}else{
			//just promoting an already existing key
			initCorrRec = corrNode;
		}
		
		if(!isFull(initCorrRec)){
			System.out.println(key + " inserted");
			return simpleInsert(key, vNumRec, initCorrRec);
		}else{
			split(key,initCorrRec);
			verdict = key + " inserted." + "SPLIT HAPPENED";
		}

		return verdict;

	}

	public long findCorrectNode(long key) throws IOException{
		raf.seek(BYTES_PER_ENTRY);//get root note location
		long root = raf.readLong();
		long record = root;
		while(hasChild(record)){
			System.out.println(record); //tester
			long index = 2; //first key
			//formula for correct key
			raf.seek(HEADER_BYTES + record*BYTES_PER_NODE + index*BYTES_PER_ENTRY); //skips first 2 numbers in record
			long compareKey = raf.readLong();
			while( compareKey != DEF_VALUE ){
				if( key < compareKey ){
					raf.seek(HEADER_BYTES + record*BYTES_PER_NODE + (index-1)*BYTES_PER_ENTRY);// formula for correct childNode
					long child = raf.readLong();
					record = child;
					break;
				}
				else{
					if(index+3 >= NUM_POINTERS){
						raf.seek(HEADER_BYTES + record*BYTES_PER_NODE + (index+2)*BYTES_PER_ENTRY);// formula for correct childNode
						long child = raf.readLong();
						record = child;
						break;
					}
					index+=3;
					raf.seek(HEADER_BYTES + record*BYTES_PER_NODE + index*BYTES_PER_ENTRY);
					compareKey = raf.readLong();
					if( compareKey == DEF_VALUE ){
						raf.seek(HEADER_BYTES + record*BYTES_PER_NODE + (index-1)*BYTES_PER_ENTRY);// formula for correct childNode
						long child = raf.readLong();
						record = child;
						break;
					}
				}
			}
			System.out.println(record); //tester
		}
		return record;
	}
	
	public boolean hasChild(long node) throws IOException{
		long ind = HEADER_BYTES + node*BYTES_PER_NODE + BYTES_PER_ENTRY;
		boolean hasChild = true;
		raf.seek(ind);
		long val = raf.readLong();
		if( val == DEF_VALUE ) hasChild = false;
		return hasChild;
	}

	public String simpleInsert(long key, long vNumRec, long initCorrRec) throws IOException{
		String verdict = "";
		long corrInd = 0;
		for(long ind = 2; ind < MAX_KEYS*3; ind += 3){
			raf.seek(HEADER_BYTES + initCorrRec*BYTES_PER_NODE + ind*BYTES_PER_ENTRY);
			long currKey = raf.readLong();
			if(currKey == DEF_VALUE || currKey > key){
				corrInd = ind;
				break;

			}
		}
		shiftValues(initCorrRec, corrInd);
		raf.seek(HEADER_BYTES + initCorrRec*BYTES_PER_NODE + corrInd*BYTES_PER_ENTRY);
		raf.writeLong(key);
		raf.writeLong(vNumRec);
		verdict = key + " inserted.";
		return verdict;

	}


	public void split(long key, long node) throws IOException{
		boolean newParent = false;
		initRec(END_OF_FILE); //create new node for right child
		long newChildNode = btNumRec++;


		long parentNode = getParent(node);
		//long parentNode = parentNode; //default value is original node if parent does not split
		if(parentNode == DEF_VALUE){
			initRec(END_OF_FILE);
			parentNode = btNumRec++;
			newParent = true;
		}
		long[] keys = getKeys(key,node);
		System.out.println(Arrays.toString(keys)); //tester
		long[] VSRecs = getVSRecs(keys,node);
		
		//Insert latter children into new right node created
		for( int i = (ORDER/2) + 1; i < ORDER; i++ ){
			simpleInsert(keys[i], VSRecs[i], newChildNode);
		}

		//determine the places of the childIDs in the parent's record information
		long startIndex;
		if(newParent){
			startIndex = 1;
		}else{
			startIndex = getChildIndex(parentNode,node);
		}

		if(isFull(parentNode)){ //only add to stack if there's a chain split
			//add to the stack before promoting median so that if parent needs to split, it can point to the new child  (which won't be stored in the 8th position if ever)
			splitChildren.push(newChildNode); //push the new child of the parent
			splitChildren.push( ((startIndex-1)/3) + 1 ); //push which child (ex. 0,1,2,etc.) the newChild will be for the parent (weird formula was used to get simple index from 1 to number of keys instead of 0-19)
			//parentNode = btNumRec;
		}

		
		//Promote median to its proper node (if parent node is full, it will trigger a recursion)
		addValue(keys[ORDER/2],VSRecs[ORDER/2],parentNode);

		//Update child pointers of children if it has children
		if(!splitChildren.isEmpty()){
			long[] childOfChildIDs = new long[ORDER+1]; //when split happens above lowest level, it means they already have ORDER+1 children
			int tempArrInd = 0; long tempIDInd = 0;
			long indOfNewChildOfChild = splitChildren.pop();
			System.out.println(indOfNewChildOfChild); //tester
			while(tempArrInd <= ORDER){
				if(tempArrInd == indOfNewChildOfChild){
					childOfChildIDs[tempArrInd++] = splitChildren.pop();
				}else{
					//3*tempInd + 1 gives you the (tempInd+1)th key so if tempInd is 0 it gives you the index of the first key
					raf.seek(HEADER_BYTES + node*BYTES_PER_NODE + (3*tempIDInd + 1)*BYTES_PER_ENTRY); //seek to the original node (before it split) and get its child IDs
					childOfChildIDs[tempArrInd++] = raf.readLong();
					tempIDInd++;
				}
			}
			setChildforChild(node, newChildNode, childOfChildIDs); setChildforChild(node, node, childOfChildIDs);
		}
		


		//delete shifted values from left node (original node) and promoted median
		deleteShiftedValues(node);
		//Insert key to be inserted to original node if it's not the median
		if(key != keys[ORDER/2]){
			simpleInsert(key, cvs, node);
		}

		//delete shifted values from left node (original node) and promoted median
		deleteShiftedValues(node);

		//Update child pointers of parent node
		long[] childIDs = new long[MAX_SPLIT_NODES];
		childIDs[0] = node; childIDs[1] = newChildNode;
		System.out.println("Parent node is " + parentNode); //tester
		setChildPointers(parentNode, startIndex, childIDs);

		//Update parent pointers of children
		setParent(node,parentNode); setParent(newChildNode,parentNode);
		
		//if the node that splits is the root, update root number
		if( node == root ){
			root = parentNode;
			raf.seek(BYTES_PER_ENTRY);
			raf.writeLong(root);
		}

		//update record number
		raf.seek(INIT_POS);
		raf.writeLong(btNumRec);
	}

	public void deleteShiftedValues(long origNode) throws IOException{
		for(long ind = (NUM_POINTERS/2)+1; ind < NUM_POINTERS; ind += 3){
			raf.seek(HEADER_BYTES + origNode*BYTES_PER_NODE + ind*BYTES_PER_ENTRY);
			raf.writeLong(DEF_VALUE);
			raf.writeLong(DEF_VALUE);
		}
	}

	public void setChildPointers(long parentNode, long startIndex, long[] childIDs) throws IOException{
		//shift childIDs to the right to free up for insert
		shiftChildIDs(parentNode,startIndex);
		int childIndex = 0;
		for( long index = startIndex; index < startIndex + (3*MAX_SPLIT_NODES); index += 3 ){
			raf.seek(HEADER_BYTES + parentNode*BYTES_PER_NODE + index*BYTES_PER_ENTRY);
			raf.writeLong(childIDs[childIndex++]);
		}
	}

	public void setChildforChild(long origNode, long newNode, long[] childIDs) throws IOException
	{
		//childIDs is the same as childOfChildIDs just abbreviated for ease
		
		//if node is the same as the original node then copy first four else copy last four
		if(origNode == newNode){
			// int arrInd = 0;
			// for( int index = 1; index <= NUM_POINTERS/2; index += 3 ){
			// 	raf.seek(HEADER_BYTES + newNode*BYTES_PER_NODE + index*BYTES_PER_ENTRY);
			// 	System.out.println(arrInd); //tester
			// 	raf.writeLong(childIDs[arrInd++]);
			// }
		}else{
			// int arrInd = childIDs.length/2;
			// for( int index = 1; index <= NUM_POINTERS/2; index += 3 ){
			// 	raf.seek(HEADER_BYTES + newNode*BYTES_PER_NODE + index*BYTES_PER_ENTRY);
			// 	System.out.println(arrInd); //tester
			// 	raf.writeLong(childIDs[arrInd++]);
			// }
		}

		System.out.println(Arrays.toString(childIDs)); //tester

	}

	public long getChildIndex(long parentNode, long nodeToSplit) throws IOException{
		long ind = DEF_VALUE;
		for( int index = 1; index < NUM_POINTERS; index += 3 ){
			raf.seek(HEADER_BYTES + parentNode*BYTES_PER_NODE + index*BYTES_PER_ENTRY);
			long childID = raf.readLong();
			if( childID == nodeToSplit ) {
				ind = index;
				break;
			}
		}
		return ind;
	}

	public long getParent(long node) throws IOException{
		//Grabs the parent node record num
		raf.seek(HEADER_BYTES + node*BYTES_PER_NODE);
		long parentID = raf.readLong();
		return parentID;
	}

	public void setParent(long node, long newParent) throws IOException {
		//Sets the parent of a node
		raf.seek(HEADER_BYTES + node*BYTES_PER_NODE);
		raf.writeLong(newParent);
	}

	public void shiftValues(long node, long correctInd) throws IOException{
		for(long ind = 3*(MAX_KEYS)-1; ind > correctInd; ind-=3){
			//read value from previous position
			raf.seek(HEADER_BYTES + (node*BYTES_PER_NODE) + (ind-3)*BYTES_PER_ENTRY);
			long prevKey = raf.readLong();
			long prevVNumRec = raf.readLong();
			//put values into next position
			raf.seek(HEADER_BYTES+ (node*BYTES_PER_NODE) + ind*BYTES_PER_ENTRY);
			raf.writeLong(prevKey);
			raf.writeLong(prevVNumRec);

		}
	}

	public void shiftChildIDs(long node, long startIndex) throws IOException{
		for(long ind = NUM_POINTERS - 1; ind > startIndex; ind-=3){
			//read value from previous position
			raf.seek(HEADER_BYTES + (node*BYTES_PER_NODE) + (ind-3)*BYTES_PER_ENTRY);
			long prevID = raf.readLong();
			//put values into next position
			raf.seek(HEADER_BYTES+ (node*BYTES_PER_NODE) + ind*BYTES_PER_ENTRY);
			raf.writeLong(prevID);
		}
	}

	public long isCopy(long key) throws IOException{
		long node = findCorrectNode(key);
		long isCopy = DEF_VALUE;
		for(long ind = 2; ind <= NUM_POINTERS-3; ind+=3){
			raf.seek(HEADER_BYTES+ node*BYTES_PER_NODE + ind*BYTES_PER_ENTRY);
			long currKey = raf.readLong();
			if(currKey == key){
				isCopy = ind;
				break;
			}
		}
		return isCopy;
	}

	public boolean isFull(long node) throws IOException{
		raf.seek(node*BYTES_PER_NODE + HEADER_BYTES + LAST_OFFSET*BYTES_PER_ENTRY);
		long curr = raf.readLong();
		if( curr != DEF_VALUE ){
			return true;
		}
		return false;
	}

	public long[] getKeys(long key, long node) throws IOException{
		long[] keys = new long[ORDER];
		int arrInd = 0;
		for(int ind = 2; ind < NUM_POINTERS; ind+=3){
			raf.seek(HEADER_BYTES+(node*BYTES_PER_NODE)+ind*BYTES_PER_ENTRY);
			keys[arrInd++] = raf.readLong(); 

		}
		keys[arrInd] = key;
		Arrays.sort(keys);
		return keys;
	}

	public long[] getVSRecs(long[] keys, long node) throws IOException{
		long[] VSRecs = new long[ORDER];
		int arrInd = 0;
		for(int ind = 2; ind < NUM_POINTERS; ind += 3){
			raf.seek(HEADER_BYTES+(node*BYTES_PER_NODE)+ind*BYTES_PER_ENTRY);
			long currKey = raf.readLong();
			if(currKey == keys[arrInd]){ //if same then get vsnumrec vaue
				VSRecs[arrInd++] = raf.readLong();
			}
			else{
				VSRecs[arrInd++] = cvs;
				VSRecs[arrInd++] = raf.readLong();
			}

		}
		return VSRecs;
	}


	public long findVSRec(long key, long node) throws IOException{
		long vsrec = 0;
		for(long ind = 2; ind < END_OF_FILE; ind += 3){
			raf.seek(HEADER_BYTES+ node*BYTES_PER_NODE + ind*BYTES_PER_ENTRY);
			long currKey = raf.readLong();
			if(currKey == key){
				vsrec =  raf.readLong();
			}
		}
		return vsrec;
	}

	public String select(long key) throws IOException{
		String verdict = "";
		long index = isCopy(key);
		if(index == DEF_VALUE){
			verdict = "ERROR: " + key + " does not exist.";
		}else{
			raf.seek(HEADER_BYTES+(index+1)*BYTES_PER_ENTRY);
			long numOnVS = raf.readLong();
			String val = vs.getValue(numOnVS);
			verdict = key + " ==> " + val;
		}

		return verdict;
	}

	public String update(long key, String newVal) throws IOException{
		String verdict = "";
		long index = isCopy(key);
		if(index == DEF_VALUE){
			verdict = "ERROR: " + key + " does not exist.";
		}else{
			raf.seek(HEADER_BYTES+(index+1)*BYTES_PER_ENTRY);
			long numOnVS = raf.readLong();
			vs.updateVal(numOnVS,newVal);
			verdict = key + " updated" + ".";
		}
		return verdict;
	}

	public void printTree() throws IOException{
		StringBuffer sb = new StringBuffer("");
		raf.seek(0);
		long recs = raf.readLong();
		for( long i = 0; i < recs; i++ ){
			raf.seek(HEADER_BYTES + i*BYTES_PER_NODE);
			sb.append("Record " + i + ":");
			for( int j = 0; j < NUM_POINTERS; j++ ){
				long val = raf.readLong();
				if( j == NUM_POINTERS - 1 ) sb.append(" " + val + "\n");
				else sb.append(" " + val);
			}
		}
		System.out.println(sb);
	}
	
}