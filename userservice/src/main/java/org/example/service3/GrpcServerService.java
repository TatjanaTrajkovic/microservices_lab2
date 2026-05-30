package org.example.service3;

import io.grpc.stub.StreamObserver;
import org.example.service3.grpc.GetUserProfileRequest;
import org.example.service3.grpc.UserProfileResponse;
import org.example.service3.grpc.UserServiceGrpc;
import org.example.service3.repository.UserRepository;
import org.springframework.grpc.server.service.GrpcService;

@GrpcService
public class GrpcServerService extends UserServiceGrpc.UserServiceImplBase {

    private final UserRepository userRepository;

    public GrpcServerService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    // här frågar den user db om in info och skicar tillbaka till message-service som ställde frågan innan
    @Override
    public void getUserProfile(GetUserProfileRequest request,
                               StreamObserver<UserProfileResponse> responseObserver) {
        var userOpt = userRepository.findById(request.getUserId());

        UserProfileResponse response;
        if (userOpt.isPresent()) {
            var user = userOpt.get();
            response = UserProfileResponse.newBuilder()
                    .setUserId(user.getId())
                    .setUsername(user.getUsername())
                    .setEmail(user.getEmail() != null ? user.getEmail() : "")
                    .setFound(true)
                    .build();
        } else {
            response = UserProfileResponse.newBuilder().setFound(false).build();
        }

        responseObserver.onNext(response);
        responseObserver.onCompleted();
    }
}