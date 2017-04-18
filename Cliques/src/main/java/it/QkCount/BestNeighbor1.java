/* ============================================================================
 *  BestNeighbor1.java
 * ============================================================================
 * 
 *  Authors:			Yanxi Chen
 *  Description:		Get the best from all neighbor solutions, round 1
 *  					
*/

package it.QkCount;


import it.Util.CpuTimeLogger;

import java.io.IOException;
import java.io.BufferedWriter;
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.PrintWriter;
import java.util.UUID;
import java.util.Iterator;

import org.apache.hadoop.mapreduce.Mapper;
import org.apache.hadoop.mapreduce.Reducer;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.util.Tool;

public class BestNeighbor1 extends AbstractRound implements Tool {	

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

            String match = key.toString() + '\t' + value.toString();
            String change = QkCountDriver.translate(key.toString()) + 
                                '\t' + QkCountDriver.translate(value.toString());
            String file = UUID.randomUUID().toString() + ".txt";
            if(key.compareTo(value) == -1) {
                Text temp = key;
                key = value;
                value = temp;
            }
            BufferedReader br = null;
            FileReader fr = null;
            PrintWriter writer = null;
            int lines = 0;
            String sCurrentLine;
            try {
                writer = new PrintWriter(file, "UTF-8");
                fr = new FileReader("./graph.txt");
                br = new BufferedReader(fr);
                while ((sCurrentLine = br.readLine()) != null) {
                    lines += 1;
                    if(!(sCurrentLine.equals(match))) {
                        writer.println(sCurrentLine);
                    } else {
                        writer.println(change);
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                try {
                    if(writer != null) {
                        writer.close();
                    }
                    if (br != null)
                        br.close();
                    if (fr != null)
                        fr.close();
                } catch (IOException ex) {
                    ex.printStackTrace();
                }
            }
            String[] command = {"./approx.sh", "graph", Integer.toString(lines)};
            Runtime.getRuntime().exec(command).waitFor();
            fr = new FileReader("./approx.txt");
            br = new BufferedReader(fr);
            double localMin = Double.MAX_VALUE;
            while((sCurrentLine = br.readLine()) != null) {
                double count = Double.valueOf(sCurrentLine);
                if(count < localMin) {
                    localMin = count;
                }
            }
            if(localMin < QkCountDriver.cliqueSize) {
                context.write(new Text(key.toString()), new Text(value.toString() + 
                    QkCountDriver.NODE_DEGREE_SEPARATOR + Double.toString(localMin)));
            }
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

			CpuTimeLogger timeLog = new CpuTimeLogger("Best Neighbor 1", key.toString(),
					LOG_CPU_TIME);

            double localMin = Double.MAX_VALUE;
            Text min = null;
			Iterator<Text> it = values.iterator();
            int size = 0;
			while (it.hasNext()){
                Text next = it.next();
				String[] nextStr = next.toString().split(
                                            QkCountDriver.NODE_DEGREE_SEPARATOR);
                double current = Double.valueOf(nextStr[1]);
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
