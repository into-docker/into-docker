class IntoDocker < Formula
  desc "Never write another Dockerfile"
  homepage "https://github.com/into-docker/into-docker"
  version "${HOMEBREW_VERSION}"

  if OS.linux?
    url "${HOMEBREW_ASSET_URL_ALT}"
    sha256 "${HOMEBREW_SHA256_ALT}"
  else
    url "${HOMEBREW_ASSET_URL}"
    sha256 "${HOMEBREW_SHA256}"
  end

  bottle :unneeded

  def install
    bin.install "into"
  end

end
