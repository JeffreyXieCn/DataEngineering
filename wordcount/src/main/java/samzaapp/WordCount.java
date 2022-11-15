package samzaapp;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import joptsimple.OptionSet;
import org.apache.samza.application.StreamApplication;
import org.apache.samza.application.descriptors.StreamApplicationDescriptor;
import org.apache.samza.config.Config;
import org.apache.samza.operators.KV;
import org.apache.samza.operators.MessageStream;
import org.apache.samza.operators.OutputStream;
import org.apache.samza.operators.windows.Windows;
import org.apache.samza.runtime.LocalApplicationRunner;
import org.apache.samza.serializers.IntegerSerde;
import org.apache.samza.serializers.KVSerde;
import org.apache.samza.serializers.StringSerde;
import org.apache.samza.system.kafka.descriptors.KafkaInputDescriptor;
import org.apache.samza.system.kafka.descriptors.KafkaOutputDescriptor;
import org.apache.samza.system.kafka.descriptors.KafkaSystemDescriptor;
import org.apache.samza.util.CommandLine;

public class WordCount implements StreamApplication {
    private static final String KAFKA_SYSTEM_NAME = "kafka";
    private static final Map<String, String> KAFKA_DEFAULT_STREAM_CONFIGS = ImmutableMap.of("replication.factor", "1");

    private static final List<String> KAFKA_CONSUMER_ZK_CONNECT = ImmutableList.of("localhost:2181"); // local zookeeper
    private static final List<String> KAFKA_PRODUCER_BOOTSTRAP_SERVERS = ImmutableList.of("localhost:9092"); // local kafka
    private static final String INPUT_STREAM_ID = "test-input"; // topic in local kafka

//    private static final List<String> KAFKA_CONSUMER_ZK_CONNECT = ImmutableList.of("192.168.56.101:2181", "192.168.56.102:2181", "192.168.56.103:2181"); // zookeeper VMs in VirtualBox
//    private static final List<String> KAFKA_PRODUCER_BOOTSTRAP_SERVERS = ImmutableList.of("192.168.56.101:9092", "192.168.56.102:9092", "192.168.56.103:9092"); // kafka VMs (kafka cluster with 3 brokers) in VirtualBox
//    private static final String INPUT_STREAM_ID = "sample-text"; // topic in kafka VMs
    private static final String OUTPUT_STREAM_ID = "word-count-output";

    @Override
    public void describe(StreamApplicationDescriptor streamApplicationDescriptor) {
        System.out.println("Starting describe()");
        KafkaSystemDescriptor kafkaSystemDescriptor = new KafkaSystemDescriptor(KAFKA_SYSTEM_NAME)
                .withConsumerZkConnect(KAFKA_CONSUMER_ZK_CONNECT)
                .withProducerBootstrapServers(KAFKA_PRODUCER_BOOTSTRAP_SERVERS)
                .withDefaultStreamConfigs(KAFKA_DEFAULT_STREAM_CONFIGS);

        KafkaInputDescriptor<KV<String, String>> inputDescriptor =
                kafkaSystemDescriptor.getInputDescriptor(INPUT_STREAM_ID,
                        KVSerde.of(new StringSerde(), new StringSerde()));
        KafkaOutputDescriptor<KV<String, String>> outputDescriptor =
                kafkaSystemDescriptor.getOutputDescriptor(OUTPUT_STREAM_ID,
                        KVSerde.of(new StringSerde(), new StringSerde()));

        MessageStream<KV<String, String>> lines = streamApplicationDescriptor.getInputStream(inputDescriptor);
        OutputStream<KV<String, String>> counts = streamApplicationDescriptor.getOutputStream(outputDescriptor);

        // can use below trick to print each message for troubleshooting
//    lines.map(kv -> kv.value).sink((msg, msgCollector, taskCoord) -> {
//      System.out.println(msg);
//    });

        lines
                .map(kv -> kv.value)
                .flatMap(s -> Arrays.asList(s.split("\\W+")))
                .window(Windows.keyedSessionWindow(
                        w -> w, Duration.ofSeconds(5), () -> 0, (m, prevCount) -> prevCount + 1,
                        new StringSerde(), new IntegerSerde()), "count")
                .map(windowPane ->
                        KV.of(windowPane.getKey().getKey(),
                                windowPane.getKey().getKey() + ": " + windowPane.getMessage().toString()))
                .sendTo(counts);

        System.out.println("Finishing describe()");
    }

    public static void main(String[] args) {
        CommandLine cmdLine = new CommandLine();
        OptionSet options = cmdLine.parser().parse(args);
        Config config = cmdLine.loadConfig(options);
        LocalApplicationRunner runner = new LocalApplicationRunner(new WordCount(), config);
        runner.run();
        runner.waitForFinish();
        System.out.println("Finish writing to Kafka topic: " + OUTPUT_STREAM_ID);
    }
}
