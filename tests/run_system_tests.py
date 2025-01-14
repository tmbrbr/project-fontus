import shutil
from os import path
import argparse
import pprint
import json
import sys
import subprocess
from pathlib import Path
import tempfile
from shutil import copyfile
import asyncio
import re

SCRIPT_PATH = path.dirname(path.abspath(__file__))
PROJECT_BASE_PATH = path.join(SCRIPT_PATH, '..')
ARTIFACT_DIR = path.join(PROJECT_BASE_PATH, "fontus", "build", "libs")
TESTS_DIR = path.join(PROJECT_BASE_PATH, "tests")
JARS_BASE_PATH = path.join(TESTS_DIR, "jars")
INPUTS_BASE_PATH = path.join(JARS_BASE_PATH, "inputs")

TESTS_SOURCE_DIR = path.join(TESTS_DIR, "src")
CONFIG_FILE = path.join(TESTS_DIR, "config.json")
TMPDIR_OUTPUT_DIR_SUFFIX = "instrumented"
JAR_BASE_NAMES = ["fontus"]


def check_return_value(func):
    async def wrapper_check_return_value(*args, **kwargs):
        ret_val = await func(*args, **kwargs)
        #if ret_val.return_value != 0:
        #    pprint.pprint(ret_val)
        return ret_val

    return wrapper_check_return_value


def check_return_value_sync(func):
    def wrapper_check_return_value(*args, **kwargs):
        ret_val = func(*args, **kwargs)
        #if ret_val.return_value != 0:
        #    pprint.pprint(ret_val)
        return ret_val

    return wrapper_check_return_value


def is_existing_file(file_path):
    if not file_path:
        return False
    file = Path(file_path)
    return file.exists() and file.is_file()


def copy_file(source_dir, target_dir, name):
    source_file = path.join(source_dir, name)
    dest_file = path.join(target_dir, name)
    copyfile(source_file, dest_file)


def copy_jar(target_dir, jar):
    copy_file(JARS_BASE_PATH, target_dir, jar)


def copy_source_file(target_dir, source):
    copy_file(TESTS_SOURCE_DIR, target_dir, source)


@check_return_value_sync
def run_command_sync(cwd, arguments, input_file=None):
    exec_result = subprocess.run(
        arguments,
        check=False,
        universal_newlines=True,
        stdin=input_file,
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
        cwd=cwd
    )
    return ExecutionResult(exec_result.returncode, exec_result.stdout, exec_result.stderr)


@check_return_value
async def run_command(cwd, arguments, input_file=None):
    # exec_result = subprocess.run(
    proc = await asyncio.create_subprocess_shell(
        " ".join(arguments),
        # universal_newlines=False,
        stdin=input_file,
        stdout=asyncio.subprocess.PIPE,
        stderr=asyncio.subprocess.PIPE,
        cwd=cwd
    )
    stdout, stderr = await proc.communicate()
    return ExecutionResult(proc.returncode, stdout, stderr)


def format_jar_filename(base_name, version):
    jar_name = f'{base_name}-{version}.jar'
    return path.join(ARTIFACT_DIR, jar_name)


def check_output_files_existence(version):
    for jar_base_name in JAR_BASE_NAMES:
        jar_path = format_jar_filename(jar_base_name, version)
        if not is_existing_file(jar_path):
            print(f'Required jar file {jar_path} does not exist!')
            sys.exit(1)


def compile_project(args):
    compile_result = subprocess.run(["./gradlew", "clean", "shadowJar"],
                                    check=False,
                                    universal_newlines=True,
                                    stdout=subprocess.PIPE,
                                    stderr=subprocess.PIPE,
                                    cwd=PROJECT_BASE_PATH
                                    )
    return compile_result.returncode == 0


def build_jars():
    build_result = subprocess.run(
        [
            "bash",
            "build.sh"
        ],
        check=False,
        universal_newlines=True,
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
        cwd=JARS_BASE_PATH
    )
    return build_result.returncode == 0


class ExecutionResult:
    def __init__(self, rv, stdout, stderr):
        self._stdout = stdout.decode('utf-8').replace('Starting application with Fontus Tainting!\n', '').encode('utf-8')

        pattern = re.compile('WARNING(.)*\n')
        self._stderr = pattern.sub('', stderr.decode('utf8')).encode('utf8')

        self._return_value = rv

    @property
    def return_value(self):
        return self._return_value

    @property
    def stdout(self):
        return self._stdout

    @property
    def stderr(self):
        return self._stderr

    def __repr__(self):
        return ('ExecutionResult(return_value={self._return_value!r}, '
                'stdout={self._stdout!r}, stderr={self._stderr!r})'
                ).format(self=self)

    def __eq__(self, other):
        return self._return_value == other.return_value \
               and self._stdout == other.stdout \
               and self._stderr == other.stderr


class TestCase:
    def __init__(self, test_dict):
        self._name = test_dict.get('name')
        self._source = test_dict.get('source')
        self._safe = test_dict.get('safe')
        self._source_path = path.join(TESTS_SOURCE_DIR, self._source)
        self._arguments = test_dict.get('arguments', [])
        self._failure_expected = test_dict.get('failure_expected', False)
        self._javac_version = None  # test_dict.get('javac_version')
        self._regular_result = None
        self._agent_result = None

    @property
    def name(self):
        return self._name

    @property
    def source(self):
        return self._source

    @property
    def safe(self):
        return self._safe

    @property
    def source_path(self):
        return self._source_path

    @property
    def arguments(self):
        return self._arguments

    @property
    def javac_version(self):
        return self._javac_version

    @property
    def failure_expected(self):
        return self._failure_expected

    def __repr__(self):
        return ('TestCase(name={self._name!r}, source={self._source!r}, '
                'safe_mode={self._safe!r}'
                'source_path={self._source_path!r}, '
                'arguments={self._arguments!r}, '
                'javac_version={self._javac_version!r}), '
                'failure_expected={self._failure_expected!r})').format(self=self)


class JarTestCase:
    def __init__(self, test_dict):
        self._name = test_dict.get('name')
        self._jar_file = test_dict.get('jar_file')
        self._jar_path = path.join(JARS_BASE_PATH, self._jar_file)
        self._arguments = test_dict.get('arguments', [])
        self._safe = test_dict.get('safe')
        self._copy_file = test_dict.get('copy_file')
        #if self._copy_file is not None:
        #    self._copy_file = path.join(JARS_BASE_PATH, self._copy_file)
        self._input_file = test_dict.get('input_file', None)
        if self._input_file is not None:
            self._input_file = path.join(INPUTS_BASE_PATH, self._input_file)
        self._entry_point = test_dict.get('entry_point', 'Main')
        self._failure_expected = test_dict.get('failure_expected', False)
        self._javac_version = None  # test_dict.get('javac_version')

    @property
    def name(self):
        return self._name

    @property
    def jar_file(self):
        return self._jar_file

    @property
    def jar_path(self):
        return self._jar_path

    @property
    def arguments(self):
        return self._arguments

    @property
    def safe(self):
        return self._safe

    @property
    def copy_file(self):
        return self._copy_file

    @property
    def input_file(self):
        return self._input_file

    @property
    def entry_point(self):
        return self._entry_point

    @property
    def javac_version(self):
        return self._javac_version

    @property
    def failure_expected(self):
        return self._failure_expected

    def __repr__(self):
        return ('TestCase(name={self._name!r}, jar_file={self._jar_file!r}, '
                'jar_path={self._jar_path!r}, '
                'entry_point={self._entry_point!r}, '
                'arguments={self._arguments!r}, '
                'input_file={self._input_file!r}, '
                'copy_file={self._copy_file!r}, '
                'javac_version={self._javac_version!r}), '
                'failure_expected={self._failure_expected!r})').format(self=self)


class TestResult:
    def __init__(self, test_case, regular_result, agent_result):
        self._test_case = test_case
        self._regular_result = regular_result
        self._agent_result = agent_result

    @property
    def test_case(self):
        return self._test_case

    @property
    def successful(self):
        return not (self._regular_result is None) and \
               (self._regular_result == self._agent_result)

    @property
    def regular_result(self):
        return self._regular_result

    @regular_result.setter
    def regular_result(self, value):
        self._regular_result = value

    @property
    def agent_result(self):
        return self._agent_result

    @agent_result.setter
    def agent_result(self, value):
        self._agent_result = value

    def __repr__(self):
        return ('TestResult(\n\ttest_case={self._test_case}, '
                '\n\tregular_result={self._regular_result!r}, '
                '\n\tagent_result={self._agent_result!r}\n)').format(self=self)


class Configuration:
    def __init__(self, test_cases, jar_test_cases):
        self._test_cases = test_cases
        self._jar_test_cases = jar_test_cases
        self._verbose = False
        self._fontus_verbose = False
        self._version = "0.0.1-SNAPSHOT"

    @property
    def test_cases(self):
        return self._test_cases

    @property
    def jar_test_cases(self):
        return self._jar_test_cases

    @property
    def version(self):
        return self._version

    @version.setter
    def version(self, value):
        self._version = value

    @property
    def verbose(self):
        return self._verbose

    @verbose.setter
    def verbose(self, value):
        self._verbose = value

    @property
    def fontus_verbose(self):
        return self._fontus_verbose

    @fontus_verbose.setter
    def fontus_verbose(self, value):
        self._fontus_verbose = value

    def __repr__(self):
        return ('Configuration(version={self._version!r}, verbose={self._verbose!r}, '
                'test_cases={self._test_cases!r}, '
                'jar_test_cases={self._jar_test_cases!r})').format(self=self)


def parse_config(config_path):
    if not is_existing_file(config_path):
        print('Can\'t find config file')
        sys.exit(1)

    with open(config_path, "r", encoding="utf-8") as config_in:
        config = json.load(config_in)

    # pprint.pprint(config)
    tests = []
    for test_case in config.get('single_file_tests', []):
        test = TestCase(test_case)
        tests.append(test)

    jar_tests = []
    for test_case in config.get('jar_tests', []):
        test = JarTestCase(test_case)
        jar_tests.append(test)

    return Configuration(tests, jar_tests)


class TestRunResult:
    def __init__(self, results):
        self._num_total = len(results)
        self._successful = []
        self._failed = []
        self._failed_expected = []
        self._successful_unexpected = []
        for test_result in results:
            if not test_result.successful:
                if test_result.test_case.failure_expected:
                    self._failed_expected.append(test_result)
                else:
                    self._failed.append(test_result)
            else:
                if test_result.test_case.failure_expected:
                    self._successful_unexpected.append(test_result)
                else:
                    self._successful.append(test_result)
        self._num_successful = len(self._successful)
        self._num_failed = len(self._failed)
        self._num_failed_expected = len(self._failed_expected)
        self._num_successful_unexpected = len(self._successful_unexpected)

    @property
    def num_failed(self):
        return self._num_failed

    @property
    def num_failed_expected(self):
        return self._num_failed_expected

    @property
    def num_successful_unexpected(self):
        return self._num_successful_unexpected

    def __repr__(self):
        out = (f'Total Tests: {self._num_total},'
               f' Failed: {self._num_failed} ({self._num_failed_expected} expected),'
               f' Successful: {self._num_successful}'
               f' ({self._num_successful_unexpected} unexpected)')
        if self._num_failed > 0:
            out = out + "\n\tFailed Tests:"
            for failed_test in self._failed:
                out = out + "\n" + f'\t\t{failed_test.test_case.name}'
        if self._num_successful_unexpected > 0:
            out = out + "\n\tUnexpected Successful Tests:"
            for test in self._successful_unexpected:
                out = out + "\n" + f'\t\t{test.test_case.name}'
        return out


class TestRunner:
    def __init__(self, config, safe):
        self._config = config
        self._safe = safe

    async def _run_regular_class_file_with_agent(self, cwd, name, arguments):
        fontus_jar = format_jar_filename("fontus", self._config.version)
        verbose = ',verbose' if self._config.fontus_verbose else ''
        arguments = ["java",
                     "--add-opens",
                     "java.base/jdk.internal.misc=ALL-UNNAMED",
                     "--illegal-access=permit",
                     "-classpath",
                     '.',
                     f'-javaagent:{fontus_jar}=taintmethod={self._config.taintmethod}{verbose}',
                     name
                     ] + arguments
        return await run_command(cwd, arguments)

    @staticmethod
    async def _run_regular_class_file(cwd, name, arguments):
        arguments = ["java", name] + arguments
        return await run_command(cwd, arguments)

    async def _run_agent_jar_internal(self, cwd, name, entry_point, additional_arguments, input_file):
        fontus_jar = format_jar_filename("fontus", self._config.version)
        verbose = ',verbose' if self._config.fontus_verbose else ''
        arguments = [
                        "java",
                        "--add-opens",
                        "java.base/jdk.internal.misc=ALL-UNNAMED",
                        f'-javaagent:{fontus_jar}=taintmethod={self._config.taintmethod}{verbose}',
                        '-jar',
                        name
                    ] + additional_arguments
        return await run_command(cwd, arguments, input_file)

    @staticmethod
    async def _run_jar_internal(cwd, name, additional_arguments, input_file):
        arguments = [
                        "java",
                        "-jar",
                        name
                    ] + additional_arguments
        return await run_command(cwd, arguments, input_file)

    async def _run_jar(self, cwd, name, arguments, input_file=None):
        if input_file:
            with open(input_file, "r", encoding="utf-8") as inp:
                return await self._run_jar_internal(cwd, name, arguments, inp)
        else:
            return await self._run_jar_internal(cwd, name, arguments, input_file)

    async def _run_agent_jar(self, cwd, name, entry_point, arguments, input_file=None):
        if input_file:
            with open(input_file, "r", encoding="utf-8") as inp:
                return await self._run_agent_jar_internal(cwd, name, entry_point, arguments, inp)
        else:
            return await self._run_agent_jar_internal(cwd, name, entry_point, arguments, input_file)

    @staticmethod
    async def _compile_source_file(cwd, source):
        arguments = ["javac", "-encoding", "UTF-8", source]
        exec_result = await run_command(cwd, arguments)
        return exec_result.return_value == 0

    async def _run_jar_test(self, base_dir, test):
        print(f'Running Jar Test: "{test.name}"')
        if test.copy_file is not None:
            copy_file(JARS_BASE_PATH, base_dir, test.copy_file)

        copy_jar(base_dir, test.jar_file)
        # check whether test should be run in safe mode or safe mode is activated
        regular_result, agent_result = await(asyncio.gather(
            self._run_jar(
                base_dir,
                test.jar_file,
                test.arguments,
                test.input_file
            ),
            self._run_agent_jar(
                base_dir,
                test.jar_file,
                test.entry_point,
                test.arguments,
                test.input_file
            )
        ))
        return TestResult(test, regular_result, agent_result)

    async def _run_test(self, base_dir, test):
        if isinstance(test, JarTestCase):
            return await self._run_jar_test(base_dir, test)
        print(f'Running Test: "{test.name}"')
        (base_name, _, _) = test.source.partition(".java")
        copy_source_file(base_dir, test.source)
        await self._compile_source_file(base_dir, test.source)
        regular_result, agent_result = await asyncio.gather(
            self._run_regular_class_file(
                base_dir,
                base_name,
                test.arguments
            ),
            self._run_regular_class_file_with_agent(
                base_dir,
                base_name,
                test.arguments
            )
        )
        return TestResult(test, regular_result, agent_result)

    async def _run_tests(self, base_dir):
        # run all the tests concurrently and then check their results once they're all finished
        all_tests = self._config.test_cases + self._config.jar_test_cases
        test_results = await asyncio.gather(
            *(self._run_test(base_dir, test) for test in all_tests))
        for test_result in test_results:
            if not test_result.successful and not test_result.test_case.failure_expected:
                print(
                    (f'Test "{test_result.test_case.name}" failed:\n'
                     f'Regular result: "{test_result.regular_result}",\n'
                     f'Agent Result: "{test_result.agent_result}"')                )
            elif self._config.verbose:
                print(test_result)

        return test_results

    async def run_tests(self):
        with tempfile.TemporaryDirectory() as tmpdir:
            out_dir = Path(path.join(tmpdir, TMPDIR_OUTPUT_DIR_SUFFIX))
            out_dir.mkdir()
            out_verbose = Path(path.join(tmpdir, 'tmp'))
            print(f'Running tests in {out_dir}')
            test_results = await self._run_tests(tmpdir)
            if self._config.fontus_verbose:
                shutil.rmtree('./tmp')
                shutil.copytree(out_verbose, './tmp')

        return TestRunResult(test_results)


async def main(args):
    if args.build_first:
        compile_project(args)

    if check_output_files_existence(args.version) is False:
        print('Failed to compile java project!')
        sys.exit(1)

    build_jars()

    config = parse_config(args.config)
    config.version = args.version
    config.verbose = args.verbose
    config.fontus_verbose = args.fontus_verbose
    config.taintmethod = args.taint_type
    # pprint.pprint(config)
    runner = TestRunner(config, args.safe)
    await check_java_version()
    result = await runner.run_tests()
    print(result)
    sys.exit(result.num_failed)


async def check_java_version():
    proc = await asyncio.create_subprocess_shell('java -version', stdout=asyncio.subprocess.PIPE,
                                                 stderr=asyncio.subprocess.PIPE)
    stdout, stderr = await proc.communicate()
    print(stdout)
    print(stderr)


if __name__ == "__main__":
    ARG_PARSER = argparse.ArgumentParser()
    ARG_PARSER.add_argument("--build-first", action="store_true")
    ARG_PARSER.add_argument("--verbose", action="store_true")
    ARG_PARSER.add_argument("--fontus-verbose", action="store_true")
    ARG_PARSER.add_argument("--version", default="0.0.1-SNAPSHOT")
    ARG_PARSER.add_argument("--safe", action="store_true",
                            help="Runs all tests in safe mode.")
    ARG_PARSER.add_argument("--config", default=CONFIG_FILE)
    ARG_PARSER.add_argument("--taint_type", choices=['boolean', 'range',
                                                     'array', 'lazybasic', 'lazycomplex'],
                            default='boolean')

    asyncio.run(main(ARG_PARSER.parse_args()))
