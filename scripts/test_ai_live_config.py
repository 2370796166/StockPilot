"""Offline credential-loader checks using temporary synthetic settings only."""
import importlib.util
from pathlib import Path
import tempfile
import unittest


spec = importlib.util.spec_from_file_location("ai_live", Path(__file__).with_name("test-ai-live.py"))
ai_live = importlib.util.module_from_spec(spec)
spec.loader.exec_module(ai_live)


class LocalCredentialTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.scratch = Path(__file__).resolve().parents[1] / "target"
        cls.scratch.mkdir(exist_ok=True)

    def test_reads_only_deepseek_key_and_accepts_bom_comments_and_quotes(self):
        with tempfile.TemporaryDirectory(dir=self.scratch) as directory:
            project = Path(directory)
            (project / ".env").write_text(
                '# fixture only\n! ignored\nAI_PROVIDER=deepseek\n'
                'JWT_SECRET=unrelated-fixture\nAI_API_KEY="synthetic-key"\n',
                encoding="utf-8-sig",
            )
            self.assertEqual("synthetic-key", ai_live.local_key(project))

    def test_other_provider_credentials_are_not_sent_to_deepseek(self):
        with tempfile.TemporaryDirectory(dir=self.scratch) as directory:
            project = Path(directory)
            for provider in ("CUSTOM", "QWEN", ""):
                (project / ".env").write_text(
                    f"AI_PROVIDER={provider}\nAI_API_KEY=synthetic-other-provider\n",
                    encoding="utf-8",
                )
                self.assertEqual("", ai_live.local_key(project))

    def test_missing_file_or_key_never_fabricates_a_credential(self):
        with tempfile.TemporaryDirectory(dir=self.scratch) as directory:
            project = Path(directory)
            self.assertEqual("", ai_live.local_key(project))
            (project / ".env").write_text("AI_PROVIDER=DEEPSEEK\nAI_API_KEY=  \n", encoding="utf-8")
            self.assertEqual("", ai_live.local_key(project))


if __name__ == "__main__":
    unittest.main()
