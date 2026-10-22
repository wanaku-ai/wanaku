# RPM spec for the Wanaku server.
#
# This spec is a convenience template. It repackages the prebuilt Linux
# release archive and the systemd unit in deploy/systemd. Use build-rpm.sh to
# build it. Override the GitHub release that provides the archive with
# --define "release_tag <tag>".

%global debug_package %{nil}
%{!?release_tag: %global release_tag v%{version}}

Name:           wanaku
Version:        0.3.0
Release:        1%{?dist}
Summary:        Governed action proxy for AI agents

License:        Apache-2.0
URL:            https://wanaku.ai
Source0:        https://github.com/wanaku-ai/wanaku/releases/download/%{release_tag}/wanaku-server-%{version}-linux-%{_arch}.tar.gz
Source1:        wanaku.service
Source2:        wanaku.sysusers

ExclusiveArch:  x86_64 aarch64
BuildRequires:  systemd-rpm-macros
%{?systemd_requires}

%description
Wanaku sits between AI agents and backend systems. It intercepts tool calls,
agent-to-agent messages, and inference traffic, and it enforces policy,
identity, data controls, and audit in the proxy layer.

%prep
%autosetup -n wanaku-server-%{version}-linux-%{_arch}

%build

%install
install -D -m 0755 wanaku-server %{buildroot}%{_bindir}/wanaku-server
install -D -m 0644 %{SOURCE1} %{buildroot}%{_unitdir}/wanaku.service
sed -i 's|/usr/local/bin/wanaku-server|%{_bindir}/wanaku-server|' %{buildroot}%{_unitdir}/wanaku.service
install -D -m 0644 %{SOURCE2} %{buildroot}%{_sysusersdir}/wanaku.conf
install -d -m 0755 %{buildroot}%{_sysconfdir}/wanaku

%post
%systemd_post wanaku.service

%preun
%systemd_preun wanaku.service

%postun
%systemd_postun_with_restart wanaku.service

%files
%license LICENSE
%doc README.md wanaku.yaml
%{_bindir}/wanaku-server
%{_unitdir}/wanaku.service
%{_sysusersdir}/wanaku.conf
%dir %{_sysconfdir}/wanaku

%changelog
* Thu Oct 22 2026 The Wanaku Development Team - 0.3.0-1
- Initial package
