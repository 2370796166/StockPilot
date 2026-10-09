"""Compose contract checks using temporary synthetic config; no daemon or real credentials."""
import json
import os
from pathlib import Path
import subprocess
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[1]


class StartupConfigTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        scratch = ROOT / 'target'
        scratch.mkdir(exist_ok=True)
        cls.directory = tempfile.TemporaryDirectory(dir=scratch)
        cls.env_file = Path(cls.directory.name) / 'synthetic.env'
        template = (ROOT / '.env.example').read_text(encoding='utf-8-sig')
        cls.env_file.write_text(template + '\nJWT_SECRET=synthetic-startup-test-secret-32-chars\n', encoding='utf-8')
        cls.environment = os.environ.copy()
        for line in template.splitlines():
            if '=' in line and not line.lstrip().startswith('#'):
                cls.environment.pop(line.split('=', 1)[0].strip(), None)
        cls.environment.pop('COMPOSE_PROFILES', None)
        cls.environment.pop('COMPOSE_PROJECT_NAME', None)
        cls.environment['STOCKPILOT_ENV_FILE'] = str(cls.env_file)

    @classmethod
    def tearDownClass(cls):
        if not Path(cls.directory.name).resolve().is_relative_to((ROOT / 'target').resolve()):
            raise RuntimeError('Temporary directory outside test workspace')
        cls.directory.cleanup()

    def compose(self, *args):
        return subprocess.check_output(['docker', 'compose', '--env-file', str(self.env_file), *args],
                                       cwd=ROOT, env=self.environment, timeout=30)

    def test_idea_mode_does_not_start_app_services(self):
        services = self.compose('config', '--services').decode().splitlines()
        self.assertEqual({'mysql', 'redis', 'rabbitmq'}, set(services))

    def test_app_mode_wires_internal_addresses_health_and_runtime_secrets(self):
        config = json.loads(self.compose('--profile', 'app', 'config', '--format', 'json'))
        services = config['services']
        self.assertEqual({'mysql', 'redis', 'rabbitmq', 'backend', 'frontend'}, set(services))
        backend = services['backend']
        self.assertIn('mysql:3306/stockpilot', backend['environment']['DB_URL'])
        self.assertEqual('redis', backend['environment']['REDIS_HOST'])
        self.assertEqual('6379', backend['environment']['REDIS_PORT'])
        self.assertEqual('rabbitmq', backend['environment']['RABBITMQ_HOST'])
        self.assertEqual('5672', backend['environment']['RABBITMQ_PORT'])
        self.assertEqual('8085', backend['environment']['SERVER_PORT'])
        self.assertEqual('', backend['environment']['AI_API_KEY'])
        self.assertEqual('service_healthy', backend['depends_on']['mysql']['condition'])
        self.assertEqual('service_healthy', services['frontend']['depends_on']['backend']['condition'])
        self.assertNotIn('env_file', services['frontend'])
        self.assertNotIn('AI_API_KEY', services['frontend'].get('environment', {}))
        self.assertNotIn('args', backend['build'])
        self.assertNotIn('args', services['frontend']['build'])
        self.assertIn('stockpilot_mysql_data', config['volumes'])
        for service in services.values():
            self.assertNotIn('container_name', service)
            for port in service.get('ports', []):
                self.assertEqual('127.0.0.1', port['host_ip'])

    def test_template_has_unique_names_and_all_agent_settings(self):
        keys = [line.split('=', 1)[0].strip() for line in (ROOT / '.env.example').read_text().splitlines()
                if '=' in line and not line.lstrip().startswith('#')]
        self.assertEqual(len(keys), len(set(keys)))
        self.assertTrue({'AI_AGENT_MAX_ROUNDS', 'AI_AGENT_MAX_TOOL_CALLS', 'AI_AGENT_SESSION_TTL',
                         'BACKEND_HOST_PORT', 'FRONTEND_HOST_PORT'}.issubset(keys))
        for path in (ROOT / '.dockerignore', ROOT / 'frontend/.dockerignore'):
            rules = path.read_text().splitlines()
            self.assertIn('**', rules)
            self.assertIn('**/.env', rules)
            self.assertIn('**/.env.*', rules)
            self.assertFalse(any(rule.startswith('!') and '.env' in rule for rule in rules))


if __name__ == '__main__':
    unittest.main()
