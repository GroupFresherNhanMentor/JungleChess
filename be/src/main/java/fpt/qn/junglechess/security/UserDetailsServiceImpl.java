package fpt.qn.junglechess.security;

import org.springframework.security.core.userdetails.ReactiveUserDetailsService;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import fpt.qn.junglechess.user.repository.UserRepository;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class UserDetailsServiceImpl implements ReactiveUserDetailsService {

    UserRepository userRepository;

    @Override
    public Mono<UserDetails> findByUsername(String username) {
        return Mono.fromCallable(() ->
                userRepository.findByUsername(username)
                        .map(record -> (UserDetails) UserPrincipal.from(record))
                        .orElseThrow(() -> new UsernameNotFoundException("User not found: " + username))
        ).subscribeOn(Schedulers.boundedElastic());
    }
}
