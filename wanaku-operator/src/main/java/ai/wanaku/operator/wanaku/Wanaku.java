package ai.wanaku.operator.wanaku;

import io.fabric8.kubernetes.api.model.Namespaced;
import io.fabric8.kubernetes.client.CustomResource;
import io.fabric8.kubernetes.model.annotation.Group;
import io.fabric8.kubernetes.model.annotation.Version;

@Version("v1alpha1")
@Group("wanaku.ai")
public class Wanaku extends CustomResource<WanakuSpec, WanakuStatus> implements Namespaced {}
