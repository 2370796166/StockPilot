"""Opt-in DeepSeek acceptance with synthetic business fixtures.

Run local Maven tests first, then: python scripts/test-ai-live.py
The key comes from the local .env, or a hidden prompt with --prompt-key.
It is sent to the Java probe through stdin only and never written by this script.
Default mode uses synthetic services; --agent creates and removes a dedicated MySQL schema.
At most 28 model requests, 1200 output tokens each. Never run beside a Maven rebuild.
"""
import getpass
import argparse
import json
import os
from pathlib import Path
import shutil
import subprocess
import sys
import urllib.error
import urllib.request
import xml.etree.ElementTree as ET


def local_key(project):
    path = project / ".env"
    if not path.exists():
        return ""
    settings = {}
    for line in path.read_text(encoding="utf-8-sig").splitlines():
        if not line.strip() or line.lstrip().startswith(("#", "!")) or "=" not in line:
            continue
        name, value = line.split("=", 1)
        value = value.strip()
        if len(value) >= 2 and value[0] == value[-1] and value[0] in "\"'":
            value = value[1:-1]
        settings[name.strip()] = value
    if settings.get("AI_PROVIDER", "").upper() != "DEEPSEEK":
        return ""
    return settings.get("AI_API_KEY", "").strip()


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--case", choices=("all", "permission_filter", "decline_only", "business_queries"), default="all")
    parser.add_argument("--build-directory", type=Path, help="Use compiled tests from an isolated build; credentials still come from the original project")
    parser.add_argument("--prompt-key", action="store_true", help="Use hidden input instead of the project's local .env")
    parser.add_argument("--agent", action="store_true", help="Run Agent with a dedicated MySQL database and real HTTP/JWT")
    parser.add_argument("--browser-live", action="store_true", help="Keep a dedicated real-model backend open for browser acceptance")
    parser.add_argument("--agent-synthetic", action="store_true", help="Run the real Agent/model with explicitly synthetic public Service data, without MySQL")
    options = parser.parse_args()
    options.agent = options.agent or options.browser_live
    if options.agent and options.agent_synthetic:
        parser.error("Choose MySQL Agent or synthetic Agent, not both")
    if options.case in ("decline_only", "business_queries") and not options.agent:
        parser.error("This case requires --agent")
    project = Path(__file__).resolve().parents[1]
    build = options.build_directory.resolve() if options.build_directory else project / "target"
    report = build / "surefire-reports/TEST-com.stockpilot.ai.AiAssistantServiceTest.xml"
    probe_name = "DeepSeekAgentAcceptance" if options.agent else "DeepSeekLiveAcceptance"
    probe = build / ("test-classes/com/stockpilot/ai/" + probe_name + ".class")
    if not report.exists() or not probe.exists():
        print("Run mvn -s .mvn/settings.xml clean test before live acceptance.", flush=True)
        return 2
    classpath = next((p.attrib["value"] for p in ET.parse(report).findall(".//property")
                      if p.attrib.get("name") == "java.class.path"), None)
    if not classpath:
        print("Test runtime classpath unavailable.", flush=True)
        return 2
    try:
        key = getpass.getpass("DeepSeek test key (hidden): ").strip() if options.prompt_key else local_key(project)
    except (OSError, UnicodeError):
        print("Local credential file unavailable.", flush=True)
        return 2
    if not key:
        print("No DeepSeek credential configured in local .env; use --prompt-key for hidden input.", flush=True)
        return 2
    # The destination is fixed, HTTPS only, and redirects are rejected.
    class NoRedirect(urllib.request.HTTPRedirectHandler):
        def redirect_request(self, req, fp, code, msg, headers, newurl):
            return None
    try:
        request = urllib.request.Request("https://api.deepseek.com/models",
                                         headers={"Authorization": "Bearer " + key})
        with urllib.request.build_opener(NoRedirect).open(request, timeout=20) as response:
            body = response.read(65537)
        if len(body) > 65536:
            print("Model discovery response exceeds limit.", flush=True)
            return 2
        ids = {v["id"] for v in json.loads(body).get("data", []) if isinstance(v, dict) and isinstance(v.get("id"), str)}
        model = next((v for v in ("deepseek-flash", "deepseek-chat", "deepseek-v4-pro") if v in ids), None)
        if model is None:
            print("No supported model ID returned by official model discovery.", flush=True)
            return 2
        print("MODEL_DISCOVERY_OK model=" + model, flush=True)
        java = shutil.which("java")
        if not java:
            print("Java runtime unavailable.", flush=True)
            return 2
        # The stored key is never copied into command arguments or the child environment.
        child_env = os.environ.copy()
        child_env.pop("AI_API_KEY", None)
        if options.browser_live:
            with subprocess.Popen([java, "-Dfile.encoding=UTF-8", "-cp", classpath,
                                   "com.stockpilot.ai." + probe_name],
                                  stdin=subprocess.PIPE, stdout=subprocess.PIPE, stderr=subprocess.STDOUT,
                                  text=True, encoding="utf-8", cwd=project, env=child_env) as child:
                child.stdin.write(json.dumps({"apiKey": key, "model": model, "mode": "live-browser"}) + "\n")
                child.stdin.close()
                for line in child.stdout:
                    print(line.replace(key, "[redacted]"), end="", flush=True)
                return child.wait()
        result = subprocess.run([java, "-Dfile.encoding=UTF-8", "-cp", classpath,
                                 "com.stockpilot.ai." + probe_name],
                                input=json.dumps({"apiKey": key, "model": model, "case": options.case,
                                                  "mode": "agent-synthetic" if options.agent_synthetic else ""}),
                                text=True, encoding="utf-8", cwd=project, env=child_env, timeout=600,
                                stdout=subprocess.PIPE, stderr=subprocess.STDOUT)
        # Defence in depth: sanitize child output before showing anything or writing a report.
        output = result.stdout.replace(key, "[redacted]")
        print(output, end="", flush=True)
        return result.returncode
    except urllib.error.HTTPError as error:
        print("MODEL_DISCOVERY_FAILED httpStatus=" + str(error.code), flush=True)
        return 2
    except subprocess.TimeoutExpired:
        print("LIVE_ACCEPTANCE_TIME_LIMIT", flush=True)
        return 2
    except Exception:
        # Exception strings and upstream response bodies may contain private request information.
        print("LIVE_ACCEPTANCE_TRANSPORT_OR_RUNTIME_FAILURE", flush=True)
        return 2
    finally:
        key = ""


if __name__ == "__main__":
    sys.exit(main())
