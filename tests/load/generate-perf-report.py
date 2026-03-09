#!/usr/bin/env python3
"""
Wanaku Performance Report Generator

Parses k6 JSON summary files and system monitoring logs to produce
a Markdown comparison report between baseline and patched runs.
"""

import argparse
import json
import os
import re
import sys
from datetime import datetime
from pathlib import Path


def parse_k6_summary(filepath):
    """Parse a k6 --summary-export JSON file."""
    with open(filepath) as f:
        data = json.load(f)

    metrics = {}
    for name, values in data.get("metrics", {}).items():
        if isinstance(values, dict) and "values" in values:
            metrics[name] = values["values"]
        elif isinstance(values, dict):
            metrics[name] = values

    return {
        "root_group": data.get("root_group", {}),
        "metrics": metrics,
    }


def extract_vus_from_filename(filename):
    """Extract VU count from filename like test-summary-vus-1000.json."""
    m = re.search(r"vus-(\d+)", filename)
    return int(m.group(1)) if m else 0


def collect_summaries(result_dir):
    """Collect all k6 summary JSON files from a test result directory."""
    summaries = {}
    if not os.path.isdir(result_dir):
        return summaries

    for f in sorted(os.listdir(result_dir)):
        if f.startswith("test-summary-vus-") and f.endswith(".json"):
            vus = extract_vus_from_filename(f)
            try:
                summaries[vus] = parse_k6_summary(os.path.join(result_dir, f))
            except (json.JSONDecodeError, OSError) as e:
                print(f"Warning: could not parse {f}: {e}", file=sys.stderr)

    return summaries


def parse_vmstat_log(filepath):
    """Parse vmstat log and extract CPU/memory averages."""
    if not os.path.isfile(filepath):
        return None

    cpu_user, cpu_sys, cpu_idle, mem_free, mem_active = [], [], [], [], []
    with open(filepath) as f:
        for line in f:
            parts = line.split()
            # vmstat output: r b swpd free buff cache si so bi bo in cs us sy id wa st
            if len(parts) >= 17 and parts[0].isdigit():
                try:
                    mem_free.append(int(parts[3]))
                    cpu_user.append(int(parts[12]))
                    cpu_sys.append(int(parts[13]))
                    cpu_idle.append(int(parts[14]))
                except (ValueError, IndexError):
                    continue

    if not cpu_user:
        return None

    return {
        "avg_cpu_user": sum(cpu_user) / len(cpu_user),
        "avg_cpu_sys": sum(cpu_sys) / len(cpu_sys),
        "avg_cpu_idle": sum(cpu_idle) / len(cpu_idle),
        "avg_mem_free_kb": sum(mem_free) / len(mem_free),
        "samples": len(cpu_user),
    }


def parse_java_procs_log(filepath):
    """Parse java process monitoring log for peak RSS and CPU."""
    if not os.path.isfile(filepath):
        return None

    max_rss = 0
    max_cpu = 0.0
    samples = 0
    total_cpu = 0.0

    with open(filepath) as f:
        for line in f:
            m = re.search(r"CPU=([\d.]+)%\s+MEM=([\d.]+)%\s+RSS=(\d+)KB", line)
            if m:
                cpu = float(m.group(1))
                rss = int(m.group(3))
                max_rss = max(max_rss, rss)
                max_cpu = max(max_cpu, cpu)
                total_cpu += cpu
                samples += 1

    if samples == 0:
        return None

    return {
        "peak_rss_mb": max_rss / 1024,
        "peak_cpu_pct": max_cpu,
        "avg_cpu_pct": total_cpu / samples,
        "samples": samples,
    }


def get_metric_value(metrics, metric_name, stat):
    """Safely get a metric value from k6 summary."""
    m = metrics.get(metric_name, {})
    if isinstance(m, dict):
        return m.get(stat)
    return None


def fmt_duration(ms):
    """Format milliseconds as a human-readable duration."""
    if ms is None:
        return "N/A"
    if ms < 1:
        return f"{ms * 1000:.0f}us"
    if ms < 1000:
        return f"{ms:.2f}ms"
    return f"{ms / 1000:.2f}s"


def fmt_rate(val):
    """Format a rate value."""
    if val is None:
        return "N/A"
    return f"{val:.2f}/s"


def fmt_count(val):
    """Format a count value."""
    if val is None:
        return "N/A"
    if isinstance(val, float):
        return f"{val:.0f}"
    return str(val)


def fmt_pct_change(baseline, patched):
    """Format percentage change between baseline and patched values."""
    if baseline is None or patched is None or baseline == 0:
        return "N/A"
    change = ((patched - baseline) / abs(baseline)) * 100
    if abs(change) < 0.5:
        return "~0%"
    sign = "+" if change > 0 else ""
    return f"{sign}{change:.1f}%"


def fmt_pct_change_inverted(baseline, patched):
    """Format percentage change where lower is better (latency)."""
    if baseline is None or patched is None or baseline == 0:
        return "N/A"
    change = ((patched - baseline) / abs(baseline)) * 100
    if abs(change) < 0.5:
        return "~0%"
    sign = "+" if change > 0 else ""
    indicator = " :red_circle:" if change > 10 else (" :green_circle:" if change < -5 else "")
    return f"{sign}{change:.1f}%{indicator}"


def fmt_pct_change_direct(baseline, patched):
    """Format percentage change where higher is better (throughput)."""
    if baseline is None or patched is None or baseline == 0:
        return "N/A"
    change = ((patched - baseline) / abs(baseline)) * 100
    if abs(change) < 0.5:
        return "~0%"
    sign = "+" if change > 0 else ""
    indicator = " :green_circle:" if change > 5 else (" :red_circle:" if change < -10 else "")
    return f"{sign}{change:.1f}%{indicator}"


KEY_METRICS = [
    ("mcp_request_duration", "avg", "Avg MCP request duration", fmt_duration, "lower_better"),
    ("mcp_request_duration", "med", "Median MCP request duration", fmt_duration, "lower_better"),
    ("mcp_request_duration", "p(90)", "P90 MCP request duration", fmt_duration, "lower_better"),
    ("mcp_request_duration", "p(95)", "P95 MCP request duration", fmt_duration, "lower_better"),
    ("mcp_request_duration", "max", "Max MCP request duration", fmt_duration, "lower_better"),
    ("mcp_request_duration", "min", "Min MCP request duration", fmt_duration, "lower_better"),
    ("mcp_request_count", "rate", "MCP request rate", fmt_rate, "higher_better"),
    ("mcp_request_count", "count", "Total MCP requests", fmt_count, "higher_better"),
    ("mcp_request_errors", "rate", "MCP error rate", fmt_rate, "lower_better"),
    ("mcp_request_errors", "count", "Total MCP errors", fmt_count, "lower_better"),
    ("iterations", "rate", "Iteration rate", fmt_rate, "higher_better"),
    ("iterations", "count", "Total iterations", fmt_count, "higher_better"),
    ("iteration_duration", "avg", "Avg iteration duration", fmt_duration, "lower_better"),
    ("iteration_duration", "p(95)", "P95 iteration duration", fmt_duration, "lower_better"),
    ("data_sent", "rate", "Data sent rate", fmt_rate, "higher_better"),
    ("data_received", "rate", "Data received rate", fmt_rate, "higher_better"),
]


def generate_comparison_table(baseline_summaries, patched_summaries, test_name):
    """Generate a comparison Markdown section for a test."""
    all_vus = sorted(set(list(baseline_summaries.keys()) + list(patched_summaries.keys())))

    if not all_vus:
        return f"## {test_name}\n\nNo results found.\n\n"

    lines = [f"## {test_name.capitalize()} Performance Comparison\n"]

    for vus in all_vus:
        b = baseline_summaries.get(vus, {}).get("metrics", {})
        p = patched_summaries.get(vus, {}).get("metrics", {})

        lines.append(f"### {vus} Virtual Users\n")
        lines.append("| Metric | Baseline | Patched | Change |")
        lines.append("|--------|----------|---------|--------|")

        for metric_name, stat, label, fmt_fn, direction in KEY_METRICS:
            bval = get_metric_value(b, metric_name, stat)
            pval = get_metric_value(p, metric_name, stat)

            bfmt = fmt_fn(bval) if bval is not None else "N/A"
            pfmt = fmt_fn(pval) if pval is not None else "N/A"

            if direction == "lower_better":
                change = fmt_pct_change_inverted(bval, pval)
            else:
                change = fmt_pct_change_direct(bval, pval)

            lines.append(f"| {label} | {bfmt} | {pfmt} | {change} |")

        lines.append("")

    return "\n".join(lines)


def generate_system_report(eval_dir, label, test_name):
    """Generate system resource usage summary for a run."""
    result_dir = os.path.join(eval_dir, label, test_name)
    vmstat_file = os.path.join(result_dir, f"vmstat-{label}-{test_name}.log")
    java_file = os.path.join(result_dir, f"java-procs-{label}-{test_name}.log")

    vmstat = parse_vmstat_log(vmstat_file)
    java = parse_java_procs_log(java_file)

    lines = []
    if vmstat:
        lines.append(f"**System ({label}):** "
                      f"CPU user={vmstat['avg_cpu_user']:.1f}%, "
                      f"sys={vmstat['avg_cpu_sys']:.1f}%, "
                      f"idle={vmstat['avg_cpu_idle']:.1f}%, "
                      f"free_mem={vmstat['avg_mem_free_kb'] / 1024:.0f}MB "
                      f"({vmstat['samples']} samples)")
    if java:
        lines.append(f"**Java processes ({label}):** "
                      f"peak RSS={java['peak_rss_mb']:.0f}MB, "
                      f"peak CPU={java['peak_cpu_pct']:.1f}%, "
                      f"avg CPU={java['avg_cpu_pct']:.1f}% "
                      f"({java['samples']} samples)")

    return "\n".join(lines) if lines else None


def generate_executive_summary(baseline_summaries, patched_summaries, test_name):
    """Generate a brief executive summary of key changes."""
    # Compare at the highest VU level available in both
    common_vus = sorted(set(baseline_summaries.keys()) & set(patched_summaries.keys()))
    if not common_vus:
        return ""

    high_vus = common_vus[-1]
    b = baseline_summaries[high_vus].get("metrics", {})
    p = patched_summaries[high_vus].get("metrics", {})

    b_avg = get_metric_value(b, "mcp_request_duration", "avg")
    p_avg = get_metric_value(p, "mcp_request_duration", "avg")
    b_p95 = get_metric_value(b, "mcp_request_duration", "p(95)")
    p_p95 = get_metric_value(p, "mcp_request_duration", "p(95)")
    b_rate = get_metric_value(b, "mcp_request_count", "rate")
    p_rate = get_metric_value(p, "mcp_request_count", "rate")
    b_iters = get_metric_value(b, "iterations", "count")
    p_iters = get_metric_value(p, "iterations", "count")
    b_errors = get_metric_value(b, "mcp_request_errors", "count")
    p_errors = get_metric_value(p, "mcp_request_errors", "count")

    lines = [f"**{test_name.capitalize()} at {high_vus} VUs:**"]

    if b_avg is not None and p_avg is not None:
        lines.append(f"- Avg MCP latency: {fmt_duration(b_avg)} -> {fmt_duration(p_avg)} ({fmt_pct_change_inverted(b_avg, p_avg)})")
    if b_p95 is not None and p_p95 is not None:
        lines.append(f"- P95 MCP latency: {fmt_duration(b_p95)} -> {fmt_duration(p_p95)} ({fmt_pct_change_inverted(b_p95, p_p95)})")
    if b_rate is not None and p_rate is not None:
        lines.append(f"- MCP request rate: {fmt_rate(b_rate)} -> {fmt_rate(p_rate)} ({fmt_pct_change_direct(b_rate, p_rate)})")
    if b_iters is not None and p_iters is not None:
        lines.append(f"- Total iterations: {fmt_count(b_iters)} -> {fmt_count(p_iters)} ({fmt_pct_change_direct(b_iters, p_iters)})")
    if b_errors is not None and p_errors is not None:
        lines.append(f"- MCP errors: {fmt_count(b_errors)} -> {fmt_count(p_errors)}")

    return "\n".join(lines)


def main():
    parser = argparse.ArgumentParser(description="Wanaku Performance Report Generator")
    parser.add_argument("--eval-dir", required=True, help="Evaluation directory")
    parser.add_argument("--test-scope", default="all", choices=["all", "tools", "resources"])
    parser.add_argument("--output", help="Output file (default: <eval-dir>/perf-report.md)")
    args = parser.parse_args()

    eval_dir = args.eval_dir
    output_file = args.output or os.path.join(eval_dir, "perf-report.md")
    test_names = []
    if args.test_scope in ("all", "tools"):
        test_names.append("tools")
    if args.test_scope in ("all", "resources"):
        test_names.append("resources")

    report_lines = [
        "# Wanaku Performance Evaluation Report",
        "",
        f"**Generated:** {datetime.now().strftime('%Y-%m-%d %H:%M:%S')}",
        f"**Evaluation directory:** `{eval_dir}`",
        "",
        "---",
        "",
        "## Executive Summary",
        "",
    ]

    summary_parts = []
    detail_parts = []

    for test_name in test_names:
        baseline_dir = os.path.join(eval_dir, "baseline", test_name)
        patched_dir = os.path.join(eval_dir, "patched", test_name)

        baseline_summaries = collect_summaries(baseline_dir)
        patched_summaries = collect_summaries(patched_dir)

        if not baseline_summaries and not patched_summaries:
            detail_parts.append(f"## {test_name.capitalize()}\n\nNo results found.\n")
            continue

        # Executive summary
        if baseline_summaries and patched_summaries:
            exec_summary = generate_executive_summary(baseline_summaries, patched_summaries, test_name)
            if exec_summary:
                summary_parts.append(exec_summary)

        # Detailed comparison
        if baseline_summaries and patched_summaries:
            detail_parts.append(generate_comparison_table(baseline_summaries, patched_summaries, test_name))
        elif baseline_summaries:
            detail_parts.append(f"## {test_name.capitalize()}\n\nOnly baseline results available (no patched run).\n")
        else:
            detail_parts.append(f"## {test_name.capitalize()}\n\nOnly patched results available (no baseline run).\n")

        # System resources
        sys_parts = []
        for label in ("baseline", "patched"):
            sys_report = generate_system_report(eval_dir, label, test_name)
            if sys_report:
                sys_parts.append(sys_report)
        if sys_parts:
            detail_parts.append(f"### {test_name.capitalize()} - System Resources\n")
            detail_parts.extend(sys_parts)
            detail_parts.append("")

    report_lines.extend(summary_parts)
    report_lines.append("")
    report_lines.append("---")
    report_lines.append("")
    report_lines.extend(detail_parts)

    # Legend
    report_lines.extend([
        "",
        "---",
        "",
        "## Legend",
        "",
        "- :green_circle: = improvement (>5% better)",
        "- :red_circle: = regression (>10% worse)",
        "- For latency metrics, lower is better",
        "- For throughput metrics, higher is better",
        "",
    ])

    report_content = "\n".join(report_lines)

    with open(output_file, "w") as f:
        f.write(report_content)

    # Also print to stdout
    print(report_content)
    print(f"\nReport written to: {output_file}")


if __name__ == "__main__":
    main()
