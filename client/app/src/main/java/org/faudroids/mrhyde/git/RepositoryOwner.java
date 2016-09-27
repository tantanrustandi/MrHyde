package org.faudroids.mrhyde.git;

import android.support.annotation.NonNull;

import com.google.common.base.Optional;

import org.eclipse.egit.github.core.User;

import java.io.Serializable;

/**
 * A single locally available repository.
 */
public class RepositoryOwner implements Serializable {

  private final String username;
  private final Optional<String> avatarUrl;

  public RepositoryOwner(
      @NonNull String username,
      @NonNull Optional<String> avatarUrl) {
    this.username = username;
    this.avatarUrl = avatarUrl;
  }

  public String getUsername() {
    return username;
  }

  public Optional<String> getAvatarUrl() {
    return avatarUrl;
  }


  public static RepositoryOwner fromGitHubUser(User gitHubUser) {
    return new RepositoryOwner(
        gitHubUser.getLogin(),
        Optional.fromNullable(gitHubUser.getAvatarUrl())
    );
  }
}
