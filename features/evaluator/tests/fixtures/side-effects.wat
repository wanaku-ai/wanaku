;; Implements the real evaluator-action export and returns without proposing a response.
;; The allocator supports the small context used by the fuel regression tests.
(component
  (import "wanaku:evaluator/registry" (instance $registry
    (export "copy-tool-to-namespace" (func (param "tool-name" string) (param "target-namespace" string) (result bool)))))
  (import "wanaku:evaluator/log" (instance $log
    (export "info" (func (param "message" string)))))
  (alias export $registry "copy-tool-to-namespace" (func $copy))
  (alias export $log "info" (func $log-info))
  (core module $allocation
    (memory (export "memory") 1)
    (global $next (mut i32) (i32.const 1024))
    (func (export "realloc") (param i32 i32 i32) (param $size i32) (result i32)
      (local $pointer i32)
      (local.set $pointer (global.get $next))
      (global.set $next
        (i32.and
          (i32.add (i32.add (global.get $next) (local.get $size)) (i32.const 7))
          (i32.const -8)))
      (local.get $pointer)))
  (core instance $allocation-instance (instantiate $allocation))
  (alias core export $allocation-instance "memory" (core memory $memory))
  (alias core export $allocation-instance "realloc" (core func $realloc))
  (core func $copy-lowered (canon lower (func $copy) (memory $memory) (realloc $realloc)))
  (core func $log-lowered (canon lower (func $log-info) (memory $memory) (realloc $realloc)))
  (core instance $imports
    (export "memory" (memory $memory))
    (export "copy" (func $copy-lowered))
    (export "log" (func $log-lowered)))
  (core module $processor
    (import "host" "memory" (memory 1))
    (import "host" "copy" (func $copy (param i32 i32 i32 i32) (result i32)))
    (import "host" "log" (func $log (param i32 i32)))
    (data (i32.const 0) "toolprodsecret")
    (func (export "evaluate")
      (param i32 i32 i32 i32 i32 i32 i32 i32 i32 i32 i32 i32 i32 i32)
      (drop (call $copy (i32.const 0) (i32.const 4) (i32.const 4) (i32.const 4)))
      (call $log (i32.const 8) (i32.const 6))))
  (core instance $instance (instantiate $processor (with "host" (instance $imports))))
  (alias core export $instance "evaluate" (core func $evaluate))
  (type $optional-string (option string))
  (type $argument (tuple string string))
  (type $arguments (list $argument))
  (type $context (record
    (field "method" string)
    (field "namespace" string)
    (field "tool-name" $optional-string)
    (field "arguments" $arguments)
    (field "llm-result" string)
    (field "conversation-id" $optional-string)))
  (export $public-context "evaluation-context" (type $context))
  (func (export "evaluate") (param "ctx" $public-context)
    (canon lift (core func $evaluate) (memory $memory) (realloc $realloc))))
