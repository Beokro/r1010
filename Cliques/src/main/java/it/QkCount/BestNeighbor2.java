/* ============================================================================
 *  BestNeighbor2.java
 * ============================================================================
 * 
 *  Authors:			Yanxi Chen
 *  Description:		Get the best from all neighbor solutions, round 1
 *  					
*/

package it.QkCount;


import it.Util.CpuTimeLogger;

import java.io.IOException;
import java.util.Iterator;

import org.apache.hadoop.mapreduce.Mapper;
import org.apache.hadoop.mapreduce.Reducer;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.util.Tool;

public class BestNeighbor2 extends AbstractRound implements Tool {	


	@Override
	public int run(String[] args) throws Exception {
		
		super.setup(args[0], args[1]);
		this.job.setMapperClass(Map.class);
		this.job.setReducerClass(Reduce.class);


		return this.job.waitForCompletion(true) ? 0 : 1;


	}


	public static class Map extends Mapper<Text, Text, Text, Text> {

		@Override
		public void map(Text key,Text value,Context context) throws IOException, InterruptedException {

            
			//Removed all checks: out2 files need to have only edges from small to large nodes! are only from
			/*String[] srcPair, dstPair;

			srcPair = Checker.splitNodeAndDegree(key.toString());
			dstPair = Checker.splitNodeAndDegree(value.toString());

			String src,dst;
			int srcDegree,dstDegree;

			src = srcPair[0];
			dst = dstPair[0];

			srcDegree = Integer.parseInt(srcPair[1]);

			dstDegree = Integer.parseInt(dstPair[1]);*/

			//if(Checker.DoubleCheck(src,srcDegree,dst,dstDegree))
            context.write(new Text("haha"),  new Text(key.toString() + 
                    QkCountDriver.NODE_DEGREE_SEPARATOR + value.toString()));
		}
	}

	public static class Reduce extends Reducer<Text, Text, Text, Text> {

		int CLIQUE_SIZE;
		boolean LOG_CPU_TIME;
		
		
		@Override
		protected void setup(Context context) throws IOException,
				InterruptedException {

			super.setup(context);
			this.CLIQUE_SIZE = context.getConfiguration().getInt(QkCountDriver.CLIQUE_SIZE_CONF_KEY, -1);
			this.LOG_CPU_TIME = context.getConfiguration().getBoolean(QkCountDriver.LOG_CPUTIME_CONF_KEY, true);
		}


		@Override
		public void reduce(Text key,
				Iterable<Text> values,
				Context context) throws IOException, InterruptedException {


			CpuTimeLogger timeLog = new CpuTimeLogger("Best Neighbor 2", key.toString(),
					LOG_CPU_TIME);

            Text min = null;
            double localMin = Double.MAX_VALUE;
			Iterator<Text> it = values.iterator();
            int size = 0;
			while (it.hasNext()){
				Text next = it.next();
				String[] nextStr = next.toString().split(
                                            QkCountDriver.NODE_DEGREE_SEPARATOR);
                double current = Double.valueOf(nextStr[2]);
                if(current < localMin) {
                    localMin = current;
                    min = next;
                }
                size += 1;
			}
            String outSize = "0";
            if(min != null) {
                context.write(key, min);
                outSize = "1";
            }
			
			timeLog.logCpuTime(Integer.toString(size), outSize);
		}
	}
}