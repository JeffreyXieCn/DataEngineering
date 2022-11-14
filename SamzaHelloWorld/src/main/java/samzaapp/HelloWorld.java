package samzaapp;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

import java.util.List;
import java.util.Map;
import joptsimple.OptionSet;
import org.apache.samza.application.StreamApplication;
import org.apache.samza.application.descriptors.StreamApplicationDescriptor;
import org.apache.samza.config.Config;
import org.apache.samza.operators.KV;
import org.apache.samza.operators.MessageStream;
import org.apache.samza.operators.OutputStream;
import org.apache.samza.runtime.LocalApplicationRunner;
import org.apache.samza.serializers.KVSerde;
import org.apache.samza.serializers.StringSerde;
import org.apache.samza.system.kafka.descriptors.KafkaInputDescriptor;
import org.apache.samza.system.kafka.descriptors.KafkaOutputDescriptor;
import org.apache.samza.system.kafka.descriptors.KafkaSystemDescriptor;
import org.apache.samza.util.CommandLine;

public class HelloWorld implements StreamApplication {
  private static final String KAFKA_SYSTEM_NAME = "kafka";
  private static final Map<String, String> KAFKA_DEFAULT_STREAM_CONFIGS = ImmutableMap.of("replication.factor", "1");

  private static final List<String> KAFKA_CONSUMER_ZK_CONNECT = ImmutableList.of("localhost:2181"); // zookeeper running locally
  private static final List<String> KAFKA_PRODUCER_BOOTSTRAP_SERVERS = ImmutableList.of("localhost:9092"); // Kafka running locally

  private static final String INPUT_STREAM_ID = "test-input"; // input topic
  private static final String OUTPUT_STREAM_ID = "test-output"; // output topic

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
    System.out.println(lines.toString());
    OutputStream<KV<String, String>> counts = streamApplicationDescriptor.getOutputStream(outputDescriptor);

    lines.filter(kv -> kv.value.contains("hi")).sendTo(counts); // send messages containing "hi" to output stream
  }

  public static void main(String[] args) {
    CommandLine cmdLine = new CommandLine();
    OptionSet options = cmdLine.parser().parse(args);
    Config config = cmdLine.loadConfig(options);
    LocalApplicationRunner runner = new LocalApplicationRunner(new HelloWorld(), config);
    runner.run();
    runner.waitForFinish();
    System.out.println("Finish writing to Kafka topic: " + OUTPUT_STREAM_ID);
  }
}
