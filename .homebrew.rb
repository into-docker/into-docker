class IntoDocker < Formula
  desc "Never write another Dockerfile"
  homepage "https://github.com/into-docker/into-docker"
  version "${VERSION}"
  url "${URL}"
  sha256 "${HASH}"

  bottle :unneeded

  def install
    bin.install "into"
  end

end
