import copy
import json
import unittest

from visual_accessibility_receipt import RECEIPT, XML, build, managed_identity, validate


class VisualAccessibilityReceiptTest(unittest.TestCase):
    def test_current_receipt(self):
        validate(json.loads(RECEIPT.read_text()))

    def test_rejects_source_or_result_drift(self):
        for mutate in (
            lambda value: value["sources"][0].__setitem__("sha256", "0" * 64),
            lambda value: value["result"].__setitem__("failures", 1),
            lambda value: value["device"].__setitem__("kind", "PHYSICAL"),
        ):
            value = copy.deepcopy(build())
            mutate(value)
            with self.assertRaises(ValueError):
                validate(value)

    def test_rejects_foreign_managed_xml_properties(self):
        valid = '<testsuites><testsuite><properties><property name="device" value="pixel6Api35"/><property name="flavor" value="playInternal"/><property name="project" value=":app"/></properties></testsuite></testsuites>'
        foreign = valid.replace('value="pixel6Api35"', 'value="physicalPhone"')
        self.assertEqual("pixel6Api35", managed_identity(valid)["name"])
        self.assertNotEqual("pixel6Api35", managed_identity(foreign)["name"])


if __name__ == "__main__":
    unittest.main()
