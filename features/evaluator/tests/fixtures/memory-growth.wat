;; Implements the real evaluator-action export and grows memory one page at a time.
;; It traps when the store memory limit refuses a grow request.
;; The allocator supports the small context used by the runner tests.
(component
  (core module $processor
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
      (loop $grow
        (br_if $grow (i32.ne (memory.grow (i32.const 1)) (i32.const -1))))
      (unreachable)))
  (core instance $instance (instantiate $processor))
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
