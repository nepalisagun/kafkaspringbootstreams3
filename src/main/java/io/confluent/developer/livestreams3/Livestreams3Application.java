package io.confluent.developer.livestreams3;

import io.confluent.developer.avro.Movie;
import io.confluent.developer.proto.MovieProtos;
import io.confluent.kafka.serializers.AbstractKafkaSchemaSerDeConfig;
import io.confluent.kafka.streams.serdes.avro.SpecificAvroSerde;
import io.confluent.kafka.streams.serdes.protobuf.KafkaProtobufSerde;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.serialization.Serde;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.kstream.Consumed;
import org.apache.kafka.streams.kstream.KStream;
import org.apache.kafka.streams.kstream.Produced;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.event.ApplicationStartedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.annotation.EnableKafkaStreams;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Map;
import java.util.stream.Stream;

@SpringBootApplication
@EnableKafkaStreams
@EnableKafka
public class Livestreams3Application {

    public static void main(String[] args) {
        SpringApplication.run(Livestreams3Application.class, args);
    }
}

@Component
class Processor {


    @Value("${spring.kafka.properties.schema.registry.url}")
    String srUrl;
    @Value("${spring.kafka.properties.basic.auth.credentials.source}")
    String crSource;
    @Value("${spring.kafka.properties.schema.registry.basic.auth.user.info}")
    String authUser;


    @Autowired
    public void process(StreamsBuilder builder) {
        final Serde<Long> keySerde = Serdes.Long();
        final KStream<Long, Movie> moviesAvro = builder.stream("movie_avro", Consumed.with(Serdes.Long(), specificAvro()));
        final KStream<Long, MovieProtos.Movie> moviesProto = moviesAvro
                .mapValues(
                        value -> MovieProtos.Movie.newBuilder()
                                .setMovieId(value.getMovieId())
                                .setTitle(value.getTitle())
                                .setReleaseYear(value.getReleaseYear())
                                .build()
                );
        moviesProto.to("movie_proto", Produced.with(keySerde, specificProto()));
    }

    private KafkaProtobufSerde<MovieProtos.Movie> specificProto() {
        final Map<String, String> config = Map.of(AbstractKafkaSchemaSerDeConfig.SCHEMA_REGISTRY_URL_CONFIG, srUrl,
                "basic.auth.credential.source", crSource,
                "schema.registry.basic.auth.user", authUser
        );

        final KafkaProtobufSerde<MovieProtos.Movie> kafkaProbufSerde = new KafkaProtobufSerde<>();
        kafkaProbufSerde.configure(config, false);
        return kafkaProbufSerde;
    }

    private SpecificAvroSerde<Movie> specificAvro() {
        SpecificAvroSerde<Movie> serde = new SpecificAvroSerde<>();
        final Map<String, String> config = Map.of(AbstractKafkaSchemaSerDeConfig.SCHEMA_REGISTRY_URL_CONFIG, srUrl,
                "basic.auth.credential.source", crSource,
                "schema.registry.basic.auth.user.info", authUser
        );
        serde.configure(config, false);
        return serde;
    }
}


@Component
@RequiredArgsConstructor
class Producer {

    private final KafkaTemplate<Long, Movie> kafkaTemplate;

    @EventListener(ApplicationStartedEvent.class)
    public void produceMovie() {
        final Movie rockyIv = new Movie(1L, "Rocky IV", 1985);
        final Movie terminator = new Movie(2L, "Terminator", 1984);
        final Movie batman = new Movie(3L, "Batman", 1989);
        final Movie gadar = new Movie(4L, "Gadar", 2001);

        Stream.of(rockyIv, terminator, batman, gadar).forEach(movie -> kafkaTemplate.send("movie_avro", movie));
    }
}


@Slf4j
class Consumer {

    @KafkaListener(topics = "movie_proto")
    public void consume(ConsumerRecord<Long, MovieProtos.Movie> record) {
        log.info(record.value().toString());

    }
}
