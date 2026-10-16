;; Implements the real evaluator-action export and calls one response function.
;; Tests replace FUNCTION and PARAMETER with a single-string response import,
;; such as block/reason, warn/message, or reject-malformed/reason.
(component
  (import "wanaku:evaluator/response" (instance $response
    (export "FUNCTION" (func (param "PARAMETER" string)))))
  (alias export $response "FUNCTION" (func $respond))
  (core module $reason
    (memory (export "memory") 1)
    (data (i32.const 0) "reason"))
  (core instance $reason-instance (instantiate $reason))
  (alias core export $reason-instance "memory" (core memory $reason-memory))
  (core func $respond-lowered (canon lower (func $respond) (memory $reason-memory)))
  (core instance $host (export "respond" (func $respond-lowered)))
  (core module $processor
    (import "host" "respond" (func $respond (param i32 i32)))
    (memory (export "memory") 1)
    (global $next (mut i32) (i32.const 1024))
    (func (export "realloc") (param i32 i32 i32) (param $size i32) (result i32)
      (local $pointer i32)
      (local.set $pointer (global.get $next))
      (global.set $next
        (i32.and
          (i32.add (i32.add (global.get $next) (local.get $size)) (i32.const 7))
          (i32.const -8)))
      (local.get $pointer))
    (func (export "evaluate")
      (param i32 i32 i32 i32 i32 i32 i32 i32 i32 i32 i32 i32 i32 i32)
      (call $respond (i32.const 0) (i32.const 6))))
  (core instance $instance (instantiate $processor (with "host" (instance $host))))
  (alias core export $instance "memory" (core memory $memory))
  (alias core export $instance "realloc" (core func $realloc))
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
