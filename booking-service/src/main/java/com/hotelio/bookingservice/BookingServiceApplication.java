package com.hotelio.bookingservice;

import com.hotelio.bookingservice.service.GrpcBookingService;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

@SpringBootApplication(scanBasePackages = {"com.hotelio", "com.hotelio.monolith"})
public class BookingServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(BookingServiceApplication.class, args);
    }

    // Запускаем gRPC сервер в отдельном потоке, чтобы не блокировать Spring Boot
    @Bean
    public CommandLineRunner startGrpcServer(GrpcBookingService grpcService) {
        return args -> {
            int port = 9090;
            Server server = ServerBuilder.forPort(port)
                    .addService(grpcService)
                    .build();
            server.start();
            System.out.println("gRPC server started on port " + port);

            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                System.out.println("Shutting down gRPC server...");
                server.shutdown();
            }));

            server.awaitTermination();
        };
    }
}
