package br.gov.sp.fatec.itu.estacionatec_api.dto;

import jakarta.validation.constraints.*;
import java.time.LocalDateTime;

public final class Dados {
    private Dados() {
    }

    public record PessoaRequest(@NotBlank @Size(max = 150) String name,
            @NotBlank @Size(max = 30) String document, @Email @Size(max = 150) String email,
            @Size(max = 30) String phone, @NotBlank String type, Boolean active) {
    }

    public record PessoaResponse(Long id, String name, String document, String email,
            String phone, String type, boolean active) {
    }

    public record VeiculoRequest(@NotBlank String plate, @NotBlank @Size(max = 50) String model,
            @Size(max = 30) String color, @NotNull Long ownerId, @NotBlank String type,
            @Size(max = 50) String brand, Boolean authorized) {
    }

    public record VeiculoResponse(Long id, String plate, String model, String color, Long ownerId,
            String owner, String type, String category, String brand, boolean authorized) {
    }

    public record UsuarioRequest(@NotBlank @Size(max = 150) String name,
            @NotBlank @Size(max = 50) String username, @NotBlank @Email String email,
            @NotBlank String role, @NotBlank String status, @Size(max = 72) String password) {
    }

    public record UsuarioResponse(Long id, String name, String username, String email,
            String role, String status) {
    }

    public record LoginRequest(@NotBlank String email, @NotBlank String password) {
    }

    public record LoginResponse(String token, UsuarioResponse user) {
    }

    public record MovimentacaoRequest(@NotBlank String plate, Long imageId,
            @NotBlank @Pattern(regexp = "[0-9a-fA-F-]{36}") String requestId) {
    }

    public record MovimentacaoResponse(Long id, Long vehicleId, String plate, String owner,
            String category, String model, LocalDateTime enteredAt, LocalDateTime exitedAt,
            String gateStatus, String warning) {
    }

    public record HistoricoResponse(Long id, String plate, String owner, String ownerDocument,
            String model, LocalDateTime entry, LocalDateTime exit, String category, String brand) {
    }

    public record ImagemResponse(Long id, String plate, String owner, String time, String date,
            String type, String status, String fileName, String imagePath, boolean available) {
    }

    public record CancelaRequest(@NotNull Long eventId) {
    }

    public record Mensagem(String message) {
    }

    public record RelatorioRequest(@NotNull java.time.LocalDate start, @NotNull java.time.LocalDate end,
            @NotBlank String type, String plate, Long userId, String eventType) {
    }

    public record RelatorioResponse(Long id, String name, String date, String size, boolean available) {
    }
}
