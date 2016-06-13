import java.io.IOException;
import java.text.*;
import java.util.*;
import org.apache.hadoop.conf.Configurable;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.io.IntWritable;
import org.apache.hadoop.io.NullWritable;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapreduce.Job;
import org.apache.hadoop.mapreduce.Mapper;
import org.apache.hadoop.mapreduce.Partitioner;
import org.apache.hadoop.mapreduce.Reducer;
import org.apache.hadoop.mapreduce.lib.input.FileInputFormat;
import org.apache.hadoop.mapreduce.lib.output.FileOutputFormat;
import org.apache.hadoop.mapreduce.lib.output.TextOutputFormat;
import org.apache.hadoop.util.GenericOptionsParser;
public class PartitionedUsers {
    public static class CreationDateMapper extends
            Mapper<Object, Text, IntWritable, Text> {
        private final static SimpleDateFormat frmt = new SimpleDateFormat(
                "yyyy-MM-dd'T'HH:mm:ss.SSS");
        private IntWritable outkey = new IntWritable();
        @Override
        protected void map(Object key, Text value, Context context)
                throws IOException, InterruptedException {
            Map<String, String> parsed
                    = MRDPUtil.transformXmlToMap(value
                            .toString());
            String strDate = parsed.get("LastAccessDate");
            if (strDate != null) {
                try {
                    Calendar cal = Calendar.getInstance();
                    cal.setTime(frmt.parse(strDate));
                    outkey.set(cal.get(Calendar.YEAR));
                    context.write(outkey, value);
                } catch (ParseException e) {
                }
            }
        }
    }
    public static class CreationDatePartitioner extends
            Partitioner<IntWritable, Text> implements Configurable {
        private static final String MIN_LAST_ACCESS_DATE_YEAR
                = "min.last.access.date.year";
        private Configuration conf = null;
        private int minLastAccessDateYear = 0;
        @Override
        public int getPartition(IntWritable key, Text value, int numPartitions) {
            System.out.println("Val::" + (key.get()
                    - minLastAccessDateYear));
// E.g., partiton ID = key.get() - 2008 = 2010 - 2008 = 2
            return key.get() - minLastAccessDateYear;
        }
        @Override
        public Configuration getConf() {
            return conf;
        }
        @Override
        public void setConf(Configuration conf) {
            this.conf = conf;
// In this example, the value of minLastAccessDateYear is 2008
            minLastAccessDateYear
                    = conf.getInt(MIN_LAST_ACCESS_DATE_YEAR, 0);
        }
        public static void setMinLastAccessDate(Job job,
                int minLastAccessDateYear) {
            System.out.println("confqqq--->" + minLastAccessDateYear);
// In this example, the value of minLastAccessDateYear is 2008
            job.getConfiguration().setInt(MIN_LAST_ACCESS_DATE_YEAR,
                    minLastAccessDateYear);
        }
    }
    public static class ValueReducer extends
            Reducer<IntWritable, Text, Text, NullWritable> {
        protected void reduce(IntWritable key, Iterable<Text> values,
                Context context) throws IOException,
                InterruptedException {
            System.out.println("Reduce");
            for (Text t : values) {
                context.write(t, NullWritable.get());
            }
        }
    }
    public static void main(String[] args) throws Exception {
        Configuration conf = new Configuration();
        String[] otherArgs = new GenericOptionsParser(conf, args)
                .getRemainingArgs();
        if (otherArgs.length != 2) {
            System.err.println("Usage: PartitionedUsers <users> <outdir>");
            System.exit(2);
        }
        Job job = new Job(conf, "PartitionedUsers");
        job.setJarByClass(PartitionedUsers.class);
        job.setMapperClass(CreationDateMapper.class);
// Set custom partitioner and min last access date
        job.setPartitionerClass(CreationDatePartitioner.class);
        CreationDatePartitioner.setMinLastAccessDate(job, 2010);
// Last access dates span between 2008-2011, or 4 years
        job.setNumReduceTasks(6);
        job.setReducerClass(ValueReducer.class);
        FileInputFormat.addInputPath(job, new Path(otherArgs[0]));
        FileOutputFormat.setOutputPath(job, new Path(otherArgs[1]));
        job.setOutputKeyClass(IntWritable.class);
        job.setOutputValueClass(Text.class);
        job.setOutputFormatClass(TextOutputFormat.class);
        job.getConfiguration().set("mapred.textoutputformat.separator",
                "");
        System.exit(job.waitForCompletion(true) ? 0 : 1);
    }
}